package com.momento.server.domain.notification.service;

import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.notification.entity.NotificationDraft;
import com.momento.server.domain.notification.repository.MemoryCandidate;
import com.momento.server.domain.notification.repository.NotificationRecipientRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 날짜가 기준인 알림을 보낸다. 스케줄러가 지난번 이후 구간 {@code [from, to)} 를 넘기면, 그 구간 안에 보낼 시각이 온 알림을 보낸다.
 *
 * <ul>
 *   <li>D-Day · 수정 마감 · 추억 다시보기: 매일 {@code momento.notification.daily-time}(기본 09:00)
 *   <li>기념일: 기념일마다 정한 알림 시각
 * </ul>
 *
 * <p>같은 구간을 다시 넘겨도 중복 방지 키가 이미 보낸 알림을 건너뛴다. 그래서 스케줄러는 재시작하면 그날 0시부터 다시 넘기기만 하면 된다.
 */
@Slf4j
@Service
public class NotificationDispatchService {

  /** "개봉 7일 전부터 남은 날짜" — 알림 설정 화면의 설명이다. */
  static final int D_DAY_FROM = 7;

  /**
   * 하루 구간의 끝(제외). {@link LocalTime#MAX} 는 나노초까지 들어가 TIME 컬럼과 비교할 때 다음 날 00:00 으로 반올림돼 아무것도 고르지 않는다
   * ({@code CapsuleSchedule} 의 마감 시각과 같은 이유). 기념일 알림 시각은 분 단위로 저장되므로 23:59 까지 빠짐없이 들어온다.
   */
  private static final LocalTime END_OF_DAY = LocalTime.of(23, 59, 59);

  private final NotificationRecipientRepository recipientRepository;
  private final NotificationSender notificationSender;
  private final LocalTime dailyTime;

  public NotificationDispatchService(
      NotificationRecipientRepository recipientRepository,
      NotificationSender notificationSender,
      @Value("${momento.notification.daily-time:09:00}") LocalTime dailyTime) {
    this.recipientRepository = recipientRepository;
    this.notificationSender = notificationSender;
    this.dailyTime = dailyTime;
  }

  /**
   * {@code [from, to)} 에 보낼 시각이 온 알림을 보낸다. 자정을 넘는 구간은 날짜별로 나눈다.
   *
   * <p>알림 종류마다 따로 저장한다. 한 종류가 실패해도 나머지는 보내고, 끝에 예외를 던져 스케줄러가 구간을 넘기지 않게 한다. 다음 주기에 같은 구간을 다시 돌면 보낸
   * 것은 건너뛰고 실패한 것만 다시 보낸다.
   */
  public void dispatch(LocalDateTime from, LocalDateTime to) {
    List<RuntimeException> failures = new ArrayList<>();
    for (LocalDate day = from.toLocalDate();
        !day.isAfter(to.toLocalDate());
        day = day.plusDays(1)) {
      LocalDateTime dayStart = day.atStartOfDay();
      LocalDateTime nextDayStart = day.plusDays(1).atStartOfDay();
      LocalDateTime start = from.isAfter(dayStart) ? from : dayStart;
      LocalDateTime end = to.isBefore(nextDayStart) ? to : nextDayStart;
      if (start.isBefore(end)) {
        LocalTime endTime = end.equals(nextDayStart) ? END_OF_DAY : end.toLocalTime();
        dispatchDay(day, start.toLocalTime(), endTime, failures);
      }
    }
    if (!failures.isEmpty()) {
      IllegalStateException failure =
          new IllegalStateException("알림 " + failures.size() + "종 발송 실패. 다음 주기에 다시 보낸다.");
      failures.forEach(failure::addSuppressed);
      throw failure;
    }
  }

  private void dispatchDay(
      LocalDate day, LocalTime from, LocalTime to, List<RuntimeException> failures) {
    if (!dailyTime.isBefore(from) && dailyTime.isBefore(to)) {
      run("D-Day", () -> sendCapsuleDDay(day), failures);
      run("수정 마감", () -> sendLetterDeadline(day), failures);
      run("추억 다시보기", () -> sendMemoryReminder(day), failures);
    }
    run("기념일", () -> sendAnniversary(day, from, to), failures);
  }

  private void run(String name, Runnable job, List<RuntimeException> failures) {
    try {
      job.run();
    } catch (RuntimeException exception) {
      log.error("{} 알림 발송 실패", name, exception);
      failures.add(exception);
    }
  }

  /** 개봉 7일 전부터 하루 전까지 매일. 개봉일 당일은 공개 알림이 간다. */
  void sendCapsuleDDay(LocalDate today) {
    notificationSender.send(
        recipientRepository
            .findCapsuleDDayRecipients(
                today.plusDays(1).atStartOfDay(), today.plusDays(D_DAY_FROM + 1L).atStartOfDay())
            .stream()
            .map(
                recipient ->
                    NotificationDraft.capsuleDDay(
                        recipient.userId(),
                        recipient.capsuleId(),
                        ChronoUnit.DAYS.between(today, recipient.openAt().toLocalDate()),
                        today))
            .toList());
  }

  /** 마감일 당일. 그날 23:59:59 에 잠긴다. */
  void sendLetterDeadline(LocalDate today) {
    notificationSender.send(
        recipientRepository
            .findLetterDeadlineRecipients(today.atStartOfDay(), today.plusDays(1).atStartOfDay())
            .stream()
            .map(
                recipient ->
                    NotificationDraft.letterDeadline(
                        recipient.userId(), recipient.capsuleId(), recipient.capsuleTitle()))
            .toList());
  }

  /**
   * N년 전 오늘의 추억. 회원마다 하루에 한 건만 보내고, 여러 개면 가장 최근 해의 추억을 고른다(같은 날이면 나중에 남긴 것).
   *
   * <p>평년의 2월 28일에는 2월 29일 추억도 함께 본다. 그러지 않으면 2월 29일 추억은 4년에 한 번만 떠오른다(기념일의 {@link
   * Anniversary#nextDate} 와 같은 규칙).
   */
  void sendMemoryReminder(LocalDate today) {
    List<MemoryCandidate> candidates =
        new ArrayList<>(
            recipientRepository.findMemoryCandidates(
                today.getMonthValue(), today.getDayOfMonth(), today));
    if (today.getMonth() == Month.FEBRUARY && today.getDayOfMonth() == 28 && !today.isLeapYear()) {
      candidates.addAll(recipientRepository.findMemoryCandidates(2, 29, today));
    }

    Map<Long, MemoryCandidate> latestPerUser = new LinkedHashMap<>();
    Comparator<MemoryCandidate> latest =
        Comparator.comparing(MemoryCandidate::memoryDate).thenComparing(MemoryCandidate::memoryId);
    for (MemoryCandidate candidate : candidates) {
      latestPerUser.merge(
          candidate.userId(),
          candidate,
          (kept, next) -> latest.compare(next, kept) > 0 ? next : kept);
    }

    notificationSender.send(
        latestPerUser.values().stream()
            .map(
                memory ->
                    NotificationDraft.memoryReminder(
                        memory.userId(),
                        memory.memoryId(),
                        memory.title(),
                        today.getYear() - memory.memoryDate().getYear(),
                        today))
            .toList());
  }

  /** 알림 시각이 구간 안인 기념일 중, 오늘이 당일이거나 "며칠 전" 미리 알림 날인 것. */
  void sendAnniversary(LocalDate today, LocalTime from, LocalTime to) {
    List<NotificationDraft> drafts = new ArrayList<>();
    for (Anniversary anniversary :
        recipientRepository.findAnniversariesNotifyingBetween(from, to)) {
      long daysLeft = ChronoUnit.DAYS.between(today, anniversary.nextDate(today));
      Integer remindDaysBefore = anniversary.getRemindDaysBefore();
      if (daysLeft == 0 || (remindDaysBefore != null && daysLeft == remindDaysBefore)) {
        drafts.add(
            NotificationDraft.anniversary(
                anniversary.getUser().getId(),
                anniversary.getId(),
                anniversary.getTitle(),
                daysLeft,
                today));
      }
    }
    notificationSender.send(drafts);
  }
}

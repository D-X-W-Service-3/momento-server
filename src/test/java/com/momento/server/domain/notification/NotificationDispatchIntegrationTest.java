package com.momento.server.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.anniversary.entity.RepeatType;
import com.momento.server.domain.anniversary.repository.AnniversaryRepository;
import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.repository.MemoryRepository;
import com.momento.server.domain.notification.entity.Notification;
import com.momento.server.domain.notification.entity.NotificationType;
import com.momento.server.domain.notification.repository.NotificationRepository;
import com.momento.server.domain.notification.service.NotificationDispatchService;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 날짜가 기준인 알림의 발송 규칙을 검증한다. 스케줄러 대신 구간을 직접 넘긴다. 날짜는 먼 미래로 둬, 실제 시각에 기대는 다른 로직과 겹치지 않게 한다.
 *
 * <p>매일 알림 시각은 기본값 09:00 이다.
 */
@SpringBootTest
class NotificationDispatchIntegrationTest {

  private static final LocalDate TODAY = LocalDate.of(2045, 3, 10);
  private static final LocalDateTime MIDNIGHT = TODAY.atStartOfDay();
  private static final LocalDateTime AFTER_NINE = TODAY.atTime(9, 1);

  @Autowired private NotificationDispatchService dispatchService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private MemoryRepository memoryRepository;
  @Autowired private AnniversaryRepository anniversaryRepository;
  @Autowired private UserRepository userRepository;

  private User owner;

  @BeforeEach
  void setUp() {
    cleanUp();
    owner = saveUser("dispatch-owner", "상래");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  // ---------- 매일 09:00 ----------

  @Test
  @DisplayName("D-Day 알림은 개봉 7일 전부터 하루 전까지, 남은 일수를 담아 보낸다")
  void capsuleDDay() {
    TimeCapsule tomorrow = saveCapsule(CapsuleStatus.LOCKED, TODAY.plusDays(1));
    TimeCapsule inSeven = saveCapsule(CapsuleStatus.WRITING, TODAY.plusDays(7));
    saveCapsule(CapsuleStatus.WRITING, TODAY.plusDays(8));

    dispatchService.dispatch(MIDNIGHT, AFTER_NINE);

    assertThat(notifications(NotificationType.CAPSULE_D_DAY))
        .extracting(Notification::getReferenceId, Notification::getTitle)
        .containsExactlyInAnyOrder(
            tuple(tomorrow.getId(), "캡슐 개봉 1일 전이에요"), tuple(inSeven.getId(), "캡슐 개봉 7일 전이에요"));
  }

  @Test
  @DisplayName("매일 알림은 09:00 이 구간에 들어올 때만 보내고, 09:00 정각은 그 구간의 끝이면 아직 아니다")
  void dailyTimeBoundary() {
    saveCapsule(CapsuleStatus.WRITING, TODAY.plusDays(3));

    dispatchService.dispatch(MIDNIGHT, TODAY.atTime(9, 0));
    assertThat(notificationRepository.findAll()).isEmpty();

    dispatchService.dispatch(TODAY.atTime(9, 0), TODAY.atTime(9, 0, 1));
    assertThat(notificationRepository.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("같은 구간을 다시 넘겨도(서버 재시작) 같은 날 같은 알림은 한 번만 가고, 다음 날에는 다시 간다")
  void sameDayOnceNextDayAgain() {
    saveCapsule(CapsuleStatus.WRITING, TODAY.plusDays(3));

    dispatchService.dispatch(MIDNIGHT, AFTER_NINE);
    dispatchService.dispatch(MIDNIGHT, TODAY.atTime(15, 0));
    assertThat(notifications(NotificationType.CAPSULE_D_DAY)).hasSize(1);

    dispatchService.dispatch(TODAY.atTime(15, 0), AFTER_NINE.plusDays(1));
    assertThat(notifications(NotificationType.CAPSULE_D_DAY))
        .extracting(Notification::getTitle)
        .containsExactlyInAnyOrder("캡슐 개봉 3일 전이에요", "캡슐 개봉 2일 전이에요");
  }

  @Test
  @DisplayName("수정 마감 알림은 마감일 당일에 작성 중인 캡슐만 한 번 보낸다")
  void letterDeadline() {
    TimeCapsule today =
        timeCapsuleRepository.save(
            capsuleBuilder(CapsuleStatus.WRITING)
                .letterDeadlineAt(TODAY.atTime(23, 59, 59))
                .openAt(MIDNIGHT.plusDays(5))
                .build());
    join(today, owner);
    saveCapsule(CapsuleStatus.WRITING, TODAY.plusDays(5));

    dispatchService.dispatch(MIDNIGHT, AFTER_NINE);

    assertThat(notifications(NotificationType.LETTER_DEADLINE))
        .singleElement()
        .satisfies(
            notification -> {
              assertThat(notification.getReferenceId()).isEqualTo(today.getId());
              assertThat(notification.getContent()).isEqualTo("'캡슐' 캡슐의 편지는 오늘까지 고칠 수 있어요");
            });
  }

  @Test
  @DisplayName("추억 다시보기는 회원마다 하루 한 건, 가장 최근 해의 같은 날 추억을 보낸다")
  void memoryReminderPicksLatestYear() {
    enableMemoryReminder(owner);
    saveMemory(owner, "3년 전", TODAY.minusYears(3));
    Memory lastYear = saveMemory(owner, "작년", TODAY.minusYears(1));

    dispatchService.dispatch(MIDNIGHT, AFTER_NINE);

    assertThat(notifications(NotificationType.MEMORY_REMINDER))
        .singleElement()
        .satisfies(
            notification -> {
              assertThat(notification.getReferenceId()).isEqualTo(lastYear.getId());
              assertThat(notification.getTitle()).isEqualTo("1년 전 오늘의 추억이 있어요");
              assertThat(notification.getContent()).isEqualTo("작년");
            });
  }

  @Test
  @DisplayName("평년의 2월 28일에는 2월 29일 추억도 떠오른다")
  void memoryReminderLeapDay() {
    enableMemoryReminder(owner);
    Memory leapDay = saveMemory(owner, "윤일", LocalDate.of(2044, 2, 29));
    LocalDate feb28 = LocalDate.of(2045, 2, 28);

    dispatchService.dispatch(feb28.atStartOfDay(), feb28.atTime(9, 1));

    assertThat(notifications(NotificationType.MEMORY_REMINDER))
        .extracting(Notification::getReferenceId)
        .containsExactly(leapDay.getId());
  }

  @Test
  @DisplayName("추억 다시보기를 끈 회원(기본값)에게는 보내지 않는다")
  void memoryReminderOffByDefault() {
    saveMemory(owner, "작년", TODAY.minusYears(1));

    dispatchService.dispatch(MIDNIGHT, AFTER_NINE);

    assertThat(notifications(NotificationType.MEMORY_REMINDER)).isEmpty();
  }

  // ---------- 기념일 ----------

  @Test
  @DisplayName("기념일 당일 알림은 그 기념일의 알림 시각이 구간에 들어올 때 보낸다")
  void anniversaryOnTheDay() {
    Anniversary anniversary = saveAnniversary(TODAY, RepeatType.NONE, LocalTime.of(10, 30), null);

    dispatchService.dispatch(MIDNIGHT, TODAY.atTime(10, 30));
    assertThat(notificationRepository.findAll()).isEmpty();

    dispatchService.dispatch(TODAY.atTime(10, 30), TODAY.atTime(10, 31));
    assertThat(notifications(NotificationType.ANNIVERSARY_REMINDER))
        .singleElement()
        .satisfies(
            notification -> {
              assertThat(notification.getReferenceId()).isEqualTo(anniversary.getId());
              assertThat(notification.getTitle()).isEqualTo("오늘은 기념일이에요");
              assertThat(notification.getContent()).isEqualTo("결혼기념일");
            });
  }

  @Test
  @DisplayName("미리 알림은 정한 일수 전에만 보내고, 매년 반복이면 올해 날짜로 센다")
  void anniversaryAheadYearly() {
    saveAnniversary(TODAY.plusDays(3).minusYears(5), RepeatType.YEARLY, LocalTime.of(9, 0), 3);
    saveAnniversary(TODAY.plusDays(5), RepeatType.NONE, LocalTime.of(9, 0), 3);

    dispatchService.dispatch(MIDNIGHT, AFTER_NINE);

    assertThat(notifications(NotificationType.ANNIVERSARY_REMINDER))
        .extracting(Notification::getTitle)
        .containsExactly("기념일이 3일 남았어요");
  }

  @Test
  @DisplayName("알림을 끈 기념일과 지난 기념일(반복 없음)은 보내지 않는다")
  void anniversaryDisabledOrPast() {
    anniversaryRepository.save(
        Anniversary.create(
            owner, "끔", TODAY, RepeatType.NONE, null, false, LocalTime.of(9, 0), null));
    saveAnniversary(TODAY.minusYears(1), RepeatType.NONE, LocalTime.of(9, 0), null);

    dispatchService.dispatch(MIDNIGHT, AFTER_NINE);

    assertThat(notificationRepository.findAll()).isEmpty();
  }

  @Test
  @DisplayName("자정을 넘는 구간은 날짜별로 나눠 각 날짜의 알림을 보낸다")
  void windowAcrossMidnight() {
    LocalDate tomorrow = TODAY.plusDays(1);
    saveAnniversary(TODAY, RepeatType.NONE, LocalTime.of(23, 59), null);
    saveAnniversary(tomorrow, RepeatType.NONE, LocalTime.of(0, 0), null);

    dispatchService.dispatch(TODAY.atTime(23, 30), tomorrow.atTime(0, 1));

    assertThat(notifications(NotificationType.ANNIVERSARY_REMINDER)).hasSize(2);
  }

  private List<Notification> notifications(NotificationType type) {
    return notificationRepository.findAll().stream()
        .filter(notification -> notification.getNotificationType() == type)
        .toList();
  }

  private TimeCapsule saveCapsule(CapsuleStatus status, LocalDate openDate) {
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            capsuleBuilder(status)
                .letterDeadlineAt(openDate.minusDays(2).atTime(23, 59, 59))
                .openAt(openDate.atStartOfDay())
                .build());
    join(capsule, owner);
    return capsule;
  }

  private TimeCapsule.TimeCapsuleBuilder capsuleBuilder(CapsuleStatus status) {
    return TimeCapsule.builder()
        .creator(owner)
        .iconType("ICE_CREAM")
        .title("캡슐")
        .capsuleType(CapsuleType.GROUP)
        .visibilityType(VisibilityType.ALL_MEMBERS)
        .status(status);
  }

  private void join(TimeCapsule capsule, User user) {
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(MemberRole.OWNER)
            .joinedAt(MIDNIGHT.minusDays(30))
            .build());
  }

  private void enableMemoryReminder(User user) {
    user.getNotificationSettings().update(null, null, null, null, true);
    userRepository.save(user);
  }

  private Memory saveMemory(User user, String title, LocalDate memoryDate) {
    return memoryRepository.save(
        Memory.builder().user(user).title(title).memoryDate(memoryDate).build());
  }

  private Anniversary saveAnniversary(
      LocalDate date, RepeatType repeatType, LocalTime notifyTime, Integer remindDaysBefore) {
    return anniversaryRepository.save(
        Anniversary.create(
            owner, "결혼기념일", date, repeatType, null, true, notifyTime, remindDaysBefore));
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    notificationRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    memoryRepository.deleteAllInBatch();
    anniversaryRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}

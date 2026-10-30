package com.momento.server.domain.notification.repository;

import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.notification.entity.Notification;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 알림을 받을 회원을 찾는 조회. 캡슐 · 추억 · 기념일 도메인의 리포지토리에 알림용 쿼리를 섞지 않으려고 따로 둔다.
 *
 * <p>모든 조회가 공통으로 거른다 — 탈퇴한 회원(소프트 삭제라 멤버 행은 남는다), 삭제된 캡슐 · 추억, 해당 알림 설정을 끈 회원. 캡슐 알림은 ACTIVE 멤버만
 * 받는다(나갔거나 내보내진 멤버는 받지 않는다).
 */
public interface NotificationRecipientRepository extends Repository<Notification, Long> {

  String ACTIVE_MEMBER_OF_LIVE_CAPSULE =
      " from CapsuleMember m join m.timeCapsule c join m.user u"
          + " where m.status = com.momento.server.domain.timecapsule.entity.MemberStatus.ACTIVE"
          + " and c.deletedAt is null and u.deletedAt is null";

  String RECIPIENT =
      "select new com.momento.server.domain.notification.repository.CapsuleRecipient(c.id, c.title, c.openAt, u.id)";

  /** 열린 캡슐의 멤버. 역할과 관계없이 모두 받는다. */
  @Query(
      RECIPIENT
          + ACTIVE_MEMBER_OF_LIVE_CAPSULE
          + " and c.id in :capsuleIds and u.notificationSettings.capsuleOpened = true")
  List<CapsuleRecipient> findCapsuleOpenedRecipients(
      @Param("capsuleIds") Collection<Long> capsuleIds);

  /** 새로 들어온 본인을 뺀 멤버. */
  @Query(
      RECIPIENT
          + ACTIVE_MEMBER_OF_LIVE_CAPSULE
          + " and c.id = :capsuleId and u.id <> :joinerId and u.notificationSettings.newMember = true")
  List<CapsuleRecipient> findNewMemberRecipients(
      @Param("capsuleId") Long capsuleId, @Param("joinerId") Long joinerId);

  /** {@code [from, to)} 에 열리는, 아직 열리지 않은 캡슐의 멤버. */
  @Query(
      RECIPIENT
          + ACTIVE_MEMBER_OF_LIVE_CAPSULE
          + " and c.openAt >= :from and c.openAt < :to"
          + " and c.status <> com.momento.server.domain.timecapsule.entity.CapsuleStatus.OPENED"
          + " and u.notificationSettings.capsuleDDay = true")
  List<CapsuleRecipient> findCapsuleDDayRecipients(
      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  /**
   * {@code [from, to)} 에 편지 수정이 마감되는, 아직 쓸 수 있는 캡슐에서 편지를 쓸 수 있는 멤버. 공개 범위가 전체 공개가 아니면 수신자는 편지를 쓸 수
   * 없어 받지 않는다({@code TimeCapsule#getLetterWritingEligibility} 와 같은 규칙).
   */
  @Query(
      RECIPIENT
          + ACTIVE_MEMBER_OF_LIVE_CAPSULE
          + " and c.letterDeadlineAt >= :from and c.letterDeadlineAt < :to"
          + " and c.status = com.momento.server.domain.timecapsule.entity.CapsuleStatus.WRITING"
          + " and (m.role <> com.momento.server.domain.timecapsule.entity.MemberRole.RECIPIENT"
          + " or c.visibilityType = com.momento.server.domain.timecapsule.entity.VisibilityType.ALL_MEMBERS)"
          + " and u.notificationSettings.letterDeadline = true")
  List<CapsuleRecipient> findLetterDeadlineRecipients(
      @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

  /** {@code before} 보다 앞선 해의 같은 월 · 일 추억. 몇 년 전인지와 회원별 한 건 고르기는 호출하는 쪽이 한다. */
  @Query(
      "select new com.momento.server.domain.notification.repository.MemoryCandidate("
          + "mem.id, mem.title, mem.memoryDate, u.id)"
          + " from Memory mem join mem.user u"
          + " where mem.deletedAt is null and u.deletedAt is null"
          + " and u.notificationSettings.memoryReminder = true"
          + " and extract(month from mem.memoryDate) = :month"
          + " and extract(day from mem.memoryDate) = :day"
          + " and mem.memoryDate < :before")
  List<MemoryCandidate> findMemoryCandidates(
      @Param("month") int month, @Param("day") int day, @Param("before") LocalDate before);

  /**
   * 알림을 켜 둔 기념일 중 알림 시각이 {@code [from, to)} 인 것. 오늘이 당일 · 미리 알림 날인지는 {@link Anniversary#nextDate} 로
   * 호출하는 쪽이 정한다(매년 반복 · 2월 29일 규칙이 거기 있다).
   */
  @Query(
      "select a from Anniversary a join fetch a.user u"
          + " where a.notificationEnabled = true and u.deletedAt is null"
          + " and a.notifyTime >= :from and a.notifyTime < :to")
  List<Anniversary> findAnniversariesNotifyingBetween(
      @Param("from") LocalTime from, @Param("to") LocalTime to);
}

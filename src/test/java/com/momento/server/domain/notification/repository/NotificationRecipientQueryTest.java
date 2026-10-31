package com.momento.server.domain.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.anniversary.entity.RepeatType;
import com.momento.server.domain.anniversary.repository.AnniversaryRepository;
import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.repository.MemoryRepository;
import com.momento.server.domain.notification.entity.NotificationDraft;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
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

/** 알림 받을 회원 조회의 공통 필터(설정 · 탈퇴 · 멤버 상태 · 삭제)와 유형별 조건을 검증한다. */
@SpringBootTest
class NotificationRecipientQueryTest {

  private static final LocalDate TODAY = LocalDate.of(2045, 3, 10);
  private static final LocalDateTime MIDNIGHT = TODAY.atStartOfDay();

  @Autowired private NotificationRecipientRepository recipientRepository;
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
    owner = saveUser("recipient-owner", "상래");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("공개 알림은 ACTIVE 멤버만 받고, 설정을 껐거나 탈퇴했거나 나간 멤버는 받지 않는다")
  void openedRecipientsFilterSettingsWithdrawnAndLeft() {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.OPENED, VisibilityType.ALL_MEMBERS);
    join(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);
    User recipient = join(capsule, saveUser("r", "유빈"), MemberRole.RECIPIENT, MemberStatus.ACTIVE);
    User off = saveUser("off", "꺼둠");
    off.getNotificationSettings().update(false, null, null, null, null);
    join(capsule, userRepository.save(off), MemberRole.PARTICIPANT, MemberStatus.ACTIVE);
    User withdrawn = saveUser("gone", "탈퇴");
    withdrawn.withdraw();
    join(capsule, userRepository.save(withdrawn), MemberRole.PARTICIPANT, MemberStatus.ACTIVE);
    join(capsule, saveUser("left", "나감"), MemberRole.PARTICIPANT, MemberStatus.LEFT);

    assertThat(userIds(recipientRepository.findCapsuleOpenedRecipients(List.of(capsule.getId()))))
        .containsExactlyInAnyOrder(owner.getId(), recipient.getId());
  }

  @Test
  @DisplayName("새 참여자 알림은 들어온 본인을 빼고, 새 참여자 설정을 끈 멤버도 뺀다")
  void newMemberRecipientsExcludeJoiner() {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS);
    join(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);
    User joiner = join(capsule, saveUser("j", "민건"), MemberRole.PARTICIPANT, MemberStatus.ACTIVE);
    User off = saveUser("off", "꺼둠");
    off.getNotificationSettings().update(null, null, null, false, null);
    join(capsule, userRepository.save(off), MemberRole.PARTICIPANT, MemberStatus.ACTIVE);

    assertThat(
            userIds(recipientRepository.findNewMemberRecipients(capsule.getId(), joiner.getId())))
        .containsExactly(owner.getId());
  }

  @Test
  @DisplayName("D-Day 알림은 기간 안에 열리는, 아직 열리지 않은 캡슐만 대상이다")
  void dDayRecipientsUseOpenAtRangeAndSkipOpened() {
    TimeCapsule inRange = saveCapsule(CapsuleStatus.LOCKED, MIDNIGHT.plusDays(7));
    TimeCapsule onEnd = saveCapsule(CapsuleStatus.LOCKED, MIDNIGHT.plusDays(8));
    TimeCapsule opened = saveCapsule(CapsuleStatus.OPENED, MIDNIGHT.plusDays(3));
    TimeCapsule deleted = saveCapsule(CapsuleStatus.WRITING, MIDNIGHT.plusDays(3));
    deleted.delete(MIDNIGHT);
    timeCapsuleRepository.save(deleted);
    for (TimeCapsule capsule : List.of(inRange, onEnd, opened, deleted)) {
      join(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE);
    }

    List<CapsuleRecipient> recipients =
        recipientRepository.findCapsuleDDayRecipients(MIDNIGHT.plusDays(1), MIDNIGHT.plusDays(8));

    assertThat(recipients).extracting(CapsuleRecipient::capsuleId).containsExactly(inRange.getId());
    assertThat(recipients.get(0).openAt()).isEqualTo(MIDNIGHT.plusDays(7));
  }

  @Test
  @DisplayName("수정 마감 알림은 오늘 마감인 작성 중 캡슐에서 편지를 쓸 수 있는 멤버만 받는다")
  void deadlineRecipientsOnlyWriters() {
    TimeCapsule allMembers = saveCapsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS);
    User recipientWhoWrites =
        join(allMembers, saveUser("r1", "수신1"), MemberRole.RECIPIENT, MemberStatus.ACTIVE);
    TimeCapsule restricted =
        saveCapsule(CapsuleStatus.WRITING, VisibilityType.RECIPIENT_AND_AUTHOR);
    join(restricted, owner, MemberRole.OWNER, MemberStatus.ACTIVE);
    join(restricted, saveUser("r2", "수신2"), MemberRole.RECIPIENT, MemberStatus.ACTIVE);
    TimeCapsule tomorrow =
        timeCapsuleRepository.save(
            capsuleBuilder(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS)
                .letterDeadlineAt(MIDNIGHT.plusDays(1).withHour(23).withMinute(59).withSecond(59))
                .openAt(MIDNIGHT.plusDays(10))
                .build());
    join(tomorrow, saveUser("t", "내일"), MemberRole.OWNER, MemberStatus.ACTIVE);

    List<CapsuleRecipient> recipients =
        recipientRepository.findLetterDeadlineRecipients(MIDNIGHT, MIDNIGHT.plusDays(1));

    assertThat(userIds(recipients))
        .containsExactlyInAnyOrder(recipientWhoWrites.getId(), owner.getId());
  }

  @Test
  @DisplayName("추억 후보는 지난해 이전의 같은 월 · 일이고, 설정을 켠 회원의 삭제되지 않은 추억이다")
  void memoryCandidates() {
    owner.getNotificationSettings().update(null, null, null, null, true);
    userRepository.save(owner);
    Memory lastYear = saveMemory(owner, TODAY.minusYears(1));
    Memory twoYears = saveMemory(owner, TODAY.minusYears(2));
    saveMemory(owner, TODAY);
    saveMemory(owner, TODAY.minusYears(1).plusDays(1));
    Memory deleted = saveMemory(owner, TODAY.minusYears(3));
    memoryRepository.save(
        Memory.builder()
            .id(deleted.getId())
            .user(owner)
            .title(deleted.getTitle())
            .memoryDate(deleted.getMemoryDate())
            .deletedAt(MIDNIGHT)
            .build());
    User off = saveUser("off", "꺼둠");
    saveMemory(off, TODAY.minusYears(1));

    assertThat(
            recipientRepository.findMemoryCandidates(
                TODAY.getMonthValue(), TODAY.getDayOfMonth(), TODAY))
        .extracting(MemoryCandidate::memoryId)
        .containsExactlyInAnyOrder(lastYear.getId(), twoYears.getId());
  }

  @Test
  @DisplayName("기념일은 알림을 켰고 알림 시각이 구간 안인 것만, 끝 시각은 빼고 읽는다")
  void anniversariesNotifyingBetween() {
    Anniversary nine = saveAnniversary(owner, true, LocalTime.of(9, 0));
    saveAnniversary(owner, true, LocalTime.of(9, 1));
    saveAnniversary(owner, false, LocalTime.of(9, 0));

    assertThat(
            recipientRepository.findAnniversariesNotifyingBetween(
                LocalTime.of(9, 0), LocalTime.of(9, 1)))
        .extracting(Anniversary::getId)
        .containsExactly(nine.getId());
  }

  @Test
  @DisplayName("이미 보낸 키는 회원별로 찾는다")
  void sentKeys() {
    User other = saveUser("other", "유빈");
    notificationRepository.save(NotificationDraft.capsuleOpened(owner.getId(), 1L).toEntity(owner));

    assertThat(
            notificationRepository.findSentKeys(
                List.of(owner.getId(), other.getId()), List.of("CAPSULE_OPENED:1")))
        .containsExactly(new SentKey(owner.getId(), "CAPSULE_OPENED:1"));
  }

  private List<Long> userIds(List<CapsuleRecipient> recipients) {
    return recipients.stream().map(CapsuleRecipient::userId).toList();
  }

  private TimeCapsule saveCapsule(CapsuleStatus status, VisibilityType visibility) {
    return timeCapsuleRepository.save(
        capsuleBuilder(status, visibility)
            .letterDeadlineAt(TODAY.atTime(23, 59, 59))
            .openAt(MIDNIGHT.plusDays(10))
            .build());
  }

  private TimeCapsule saveCapsule(CapsuleStatus status, LocalDateTime openAt) {
    return timeCapsuleRepository.save(
        capsuleBuilder(status, VisibilityType.ALL_MEMBERS)
            .letterDeadlineAt(openAt.minusDays(2))
            .openAt(openAt)
            .build());
  }

  private TimeCapsule.TimeCapsuleBuilder capsuleBuilder(
      CapsuleStatus status, VisibilityType visibility) {
    return TimeCapsule.builder()
        .creator(owner)
        .iconType("ICE_CREAM")
        .title("캡슐")
        .capsuleType(CapsuleType.GROUP)
        .visibilityType(visibility)
        .status(status);
  }

  private User join(TimeCapsule capsule, User user, MemberRole role, MemberStatus status) {
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(role)
            .status(status)
            .joinedAt(MIDNIGHT)
            .build());
    return user;
  }

  private Memory saveMemory(User user, LocalDate memoryDate) {
    return memoryRepository.save(
        Memory.builder().user(user).title("추억").memoryDate(memoryDate).build());
  }

  private Anniversary saveAnniversary(User user, boolean enabled, LocalTime notifyTime) {
    return anniversaryRepository.save(
        Anniversary.create(user, "기념일", TODAY, RepeatType.NONE, null, enabled, notifyTime, null));
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

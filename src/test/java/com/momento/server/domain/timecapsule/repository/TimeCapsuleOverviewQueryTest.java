package com.momento.server.domain.timecapsule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import com.momento.server.domain.timecapsule.entity.CapsuleInvitee;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.InviteStatus;
import com.momento.server.domain.timecapsule.entity.InviteeStatus;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 탭 홈 섹션 쿼리와 대기 중 초대 쿼리를 검증한다.
 *
 * <p>캡슐 일시는 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌면, 지난 일시의 캡슐 상태를 바꿀 수 있다. 상태는 테스트마다 직접 정한다.
 */
@SpringBootTest
class TimeCapsuleOverviewQueryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2045, 1, 10, 12, 0);
  private static final Limit FIVE = Limit.of(5);

  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private CapsuleInviteRepository capsuleInviteRepository;
  @Autowired private CapsuleInviteeRepository capsuleInviteeRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private User me;
  private User friend;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("overview-me", "상래");
    friend = saveUser("overview-friend", "유빈");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("곧 만날 캡슐은 내가 확인하지 않은 캡슐을 개봉일 순으로 준다")
  void unopenedCapsulesByOpenAt() {
    TimeCapsule later = mine(CapsuleStatus.WRITING, NOW.plusDays(9), NOW.plusDays(20));
    TimeCapsule openedNotChecked = mine(CapsuleStatus.OPENED, NOW.minusDays(9), NOW.minusDays(1));
    TimeCapsule locked = mine(CapsuleStatus.LOCKED, NOW.minusDays(1), NOW.plusDays(5));
    TimeCapsule checked = mine(CapsuleStatus.OPENED, NOW.minusDays(9), NOW.minusDays(2));
    markOpened(checked, me);

    assertThat(ids(timeCapsuleRepository.findMyUnopenedCapsules(me.getId(), FIVE)))
        .containsExactly(openedNotChecked.getId(), locked.getId(), later.getId());
  }

  @Test
  @DisplayName("다른 멤버가 확인했어도 내가 확인하지 않았으면 곧 만날 캡슐에 나온다")
  void unopenedIsPerMember() {
    TimeCapsule capsule = mine(CapsuleStatus.OPENED, NOW.minusDays(9), NOW.minusDays(1));
    join(capsule, friend);
    markOpened(capsule, friend);

    assertThat(ids(timeCapsuleRepository.findMyUnopenedCapsules(me.getId(), FIVE)))
        .containsExactly(capsule.getId());
  }

  @Test
  @DisplayName("수정할 수 있어요는 작성 중 캡슐만, 개봉일이 아니라 마감이 가까운 순으로 준다")
  void writingCapsulesByDeadline() {
    TimeCapsule earlyOpenLateDeadline =
        mine(CapsuleStatus.WRITING, NOW.plusDays(8), NOW.plusDays(10));
    TimeCapsule lateOpenEarlyDeadline =
        mine(CapsuleStatus.WRITING, NOW.plusDays(2), NOW.plusDays(30));
    mine(CapsuleStatus.LOCKED, NOW.minusDays(1), NOW.plusDays(5));

    assertThat(ids(timeCapsuleRepository.findMyWritingCapsules(me.getId(), FIVE)))
        .containsExactly(lateOpenEarlyDeadline.getId(), earlyOpenLateDeadline.getId());
  }

  @Test
  @DisplayName("추억이 열렸어요는 열린 캡슐만, 최근에 열린 순으로 준다")
  void openedCapsulesLatestFirst() {
    TimeCapsule older = mine(CapsuleStatus.OPENED, NOW.minusDays(40), NOW.minusDays(30));
    TimeCapsule newer = mine(CapsuleStatus.OPENED, NOW.minusDays(10), NOW.minusDays(1));
    mine(CapsuleStatus.WRITING, NOW.plusDays(2), NOW.plusDays(10));

    assertThat(ids(timeCapsuleRepository.findMyOpenedCapsules(me.getId(), FIVE)))
        .containsExactly(newer.getId(), older.getId());
  }

  @Test
  @DisplayName("섹션은 최대 다섯 개다")
  void sectionsAreLimited() {
    for (int i = 0; i < 6; i++) {
      mine(CapsuleStatus.WRITING, NOW.plusDays(2 + i), NOW.plusDays(20));
    }

    assertThat(timeCapsuleRepository.findMyWritingCapsules(me.getId(), FIVE)).hasSize(5);
    assertThat(timeCapsuleRepository.findMyUnopenedCapsules(me.getId(), FIVE)).hasSize(5);
  }

  @Test
  @DisplayName("내가 연 링크에 아직 응답하지 않았으면 대기 중인 초대가 있다")
  void pendingInviteExists() {
    open(saveInvite(othersCapsule(), InviteStatus.ACTIVE, null), me, InviteeStatus.PENDING);

    assertThat(capsuleInviteeRepository.existsPendingFor(me.getId(), NOW)).isTrue();
  }

  @Test
  @DisplayName("공유 링크 하나를 여러 명이 열면 모두 대기 중이다")
  void sharedLinkIsPendingForEveryone() {
    CapsuleInvite link = saveInvite(othersCapsule(), InviteStatus.ACTIVE, null);
    User other = saveUser("overview-other", "민수");
    open(link, me, InviteeStatus.PENDING);
    open(link, other, InviteeStatus.PENDING);

    assertThat(capsuleInviteeRepository.existsPendingFor(me.getId(), NOW)).isTrue();
    assertThat(capsuleInviteeRepository.existsPendingFor(other.getId(), NOW)).isTrue();
  }

  @Test
  @DisplayName("만료 시각 전이면 대기 중이고, 만료 시각이 되면 대기 중이 아니다")
  void pendingInviteExpiresAtBoundary() {
    open(saveInvite(othersCapsule(), InviteStatus.ACTIVE, NOW), me, InviteeStatus.PENDING);

    assertThat(capsuleInviteeRepository.existsPendingFor(me.getId(), NOW.minusSeconds(1))).isTrue();
    assertThat(capsuleInviteeRepository.existsPendingFor(me.getId(), NOW)).isFalse();
  }

  @Test
  @DisplayName("수락 · 거절한 초대, 끝난 링크, 남만 연 링크, 아무도 열지 않은 링크는 대기 중이 아니다")
  void noPendingInvite() {
    open(saveInvite(othersCapsule(), InviteStatus.ACTIVE, null), me, InviteeStatus.DECLINED);
    open(saveInvite(othersCapsule(), InviteStatus.ACTIVE, null), me, InviteeStatus.ACCEPTED);
    open(saveInvite(othersCapsule(), InviteStatus.REVOKED, null), me, InviteeStatus.PENDING);
    open(
        saveInvite(othersCapsule(), InviteStatus.ACTIVE, null),
        saveUser("overview-other", "민수"),
        InviteeStatus.PENDING);
    saveInvite(othersCapsule(), InviteStatus.ACTIVE, null);

    assertThat(capsuleInviteeRepository.existsPendingFor(me.getId(), NOW)).isFalse();
  }

  @Test
  @DisplayName("삭제된 캡슐의 초대나 이미 멤버인 캡슐의 초대는 대기 중이 아니다")
  void noPendingInviteForDeletedOrJoinedCapsule() {
    TimeCapsule deleted = saveCapsule(friend, CapsuleStatus.WRITING, NOW.plusDays(2), NOW);
    open(saveInvite(deleted, InviteStatus.ACTIVE, null), me, InviteeStatus.PENDING);
    TimeCapsule joined = othersCapsule();
    join(joined, me);
    open(saveInvite(joined, InviteStatus.ACTIVE, null), me, InviteeStatus.PENDING);

    assertThat(capsuleInviteeRepository.existsPendingFor(me.getId(), NOW)).isFalse();
  }

  private List<Long> ids(List<MyCapsule> capsules) {
    return capsules.stream().map(found -> found.capsule().getId()).toList();
  }

  private TimeCapsule mine(
      CapsuleStatus status, LocalDateTime letterDeadlineAt, LocalDateTime openAt) {
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            capsuleBuilder(me, status).letterDeadlineAt(letterDeadlineAt).openAt(openAt).build());
    join(capsule, me);
    return capsule;
  }

  private TimeCapsule othersCapsule() {
    return saveCapsule(friend, CapsuleStatus.WRITING, NOW.plusDays(2), null);
  }

  private TimeCapsule saveCapsule(
      User creator, CapsuleStatus status, LocalDateTime letterDeadlineAt, LocalDateTime deletedAt) {
    return timeCapsuleRepository.save(
        capsuleBuilder(creator, status)
            .letterDeadlineAt(letterDeadlineAt)
            .openAt(letterDeadlineAt.plusDays(10))
            .deletedAt(deletedAt)
            .build());
  }

  private TimeCapsule.TimeCapsuleBuilder capsuleBuilder(User creator, CapsuleStatus status) {
    return TimeCapsule.builder()
        .creator(creator)
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
            .role(user == me ? MemberRole.OWNER : MemberRole.PARTICIPANT)
            .joinedAt(NOW.minusDays(30))
            .build());
  }

  private void markOpened(TimeCapsule capsule, User user) {
    CapsuleMember member =
        capsuleMemberRepository
            .findByTimeCapsuleIdAndUserIdAndStatus(
                capsule.getId(), user.getId(), MemberStatus.ACTIVE)
            .orElseThrow();
    transactionTemplate.executeWithoutResult(
        tx -> capsuleMemberRepository.markOpenedIfAbsent(member.getId(), NOW));
  }

  private CapsuleInvite saveInvite(
      TimeCapsule capsule, InviteStatus status, LocalDateTime expiresAt) {
    return capsuleInviteRepository.save(
        CapsuleInvite.builder()
            .timeCapsule(capsule)
            .inviter(friend)
            .inviteToken(UUID.randomUUID().toString())
            .status(status)
            .expiresAt(expiresAt)
            .build());
  }

  private void open(CapsuleInvite invite, User user, InviteeStatus status) {
    capsuleInviteeRepository.save(
        CapsuleInvitee.builder().capsuleInvite(invite).user(user).status(status).build());
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    capsuleInviteeRepository.deleteAllInBatch();
    capsuleInviteRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}

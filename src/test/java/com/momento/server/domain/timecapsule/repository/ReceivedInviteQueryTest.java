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

/**
 * 받은 초대 목록 쿼리를 검증한다. 대기 중 조건 자체는 탭 홈 배너와 같은 문자열을 쓰므로({@link TimeCapsuleOverviewQueryTest}), 여기서는
 * 목록이 그 조건을 그대로 따르는지와 응답 값 · 순서를 본다.
 */
@SpringBootTest
class ReceivedInviteQueryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2045, 1, 10, 12, 0);

  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private CapsuleInviteRepository capsuleInviteRepository;
  @Autowired private CapsuleInviteeRepository capsuleInviteeRepository;
  @Autowired private UserRepository userRepository;

  private User me;
  private User friend;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("received-me", "민건");
    friend = saveUser("received-friend", "상래");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("내가 열고 응답하지 않은 초대를 초대한 사람 닉네임 · 캡슐 정보와 함께 준다")
  void returnsPendingInviteWithDetails() {
    TimeCapsule capsule = saveCapsule(CapsuleStatus.WRITING, null);
    CapsuleInvite link = saveInvite(capsule, MemberRole.RECIPIENT, InviteStatus.ACTIVE, null);
    open(link, me, InviteeStatus.PENDING);

    List<ReceivedInvite> received = capsuleInviteeRepository.findPendingFor(me.getId(), NOW);

    assertThat(received)
        .containsExactly(
            new ReceivedInvite(
                link.getInviteToken(),
                MemberRole.RECIPIENT,
                "상래",
                capsule.getId(),
                "캡슐",
                capsule.getOpenAt()));
  }

  @Test
  @DisplayName("최근에 연 링크가 먼저다")
  void latestOpenedFirst() {
    CapsuleInvite first = saveInvite(saveCapsule(CapsuleStatus.WRITING, null));
    CapsuleInvite second = saveInvite(saveCapsule(CapsuleStatus.WRITING, null));
    open(first, me, InviteeStatus.PENDING);
    open(second, me, InviteeStatus.PENDING);

    assertThat(tokens(capsuleInviteeRepository.findPendingFor(me.getId(), NOW)))
        .containsExactly(second.getInviteToken(), first.getInviteToken());
  }

  @Test
  @DisplayName("배너에 대기 중이 아닌 초대는 목록에도 없다")
  void followsSameConditionAsBanner() {
    open(saveInvite(saveCapsule(CapsuleStatus.WRITING, null)), me, InviteeStatus.DECLINED);
    open(saveInvite(saveCapsule(CapsuleStatus.WRITING, null)), me, InviteeStatus.ACCEPTED);
    TimeCapsule active = saveCapsule(CapsuleStatus.WRITING, null);
    open(
        saveInvite(active, MemberRole.PARTICIPANT, InviteStatus.REVOKED, null),
        me,
        InviteeStatus.PENDING);
    open(
        saveInvite(active, MemberRole.PARTICIPANT, InviteStatus.ACTIVE, NOW),
        me,
        InviteeStatus.PENDING);
    open(saveInvite(saveCapsule(CapsuleStatus.WRITING, NOW)), me, InviteeStatus.PENDING);
    open(saveInvite(saveCapsule(CapsuleStatus.LOCKED, null)), me, InviteeStatus.PENDING);
    TimeCapsule joined = saveCapsule(CapsuleStatus.WRITING, null);
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(joined)
            .user(me)
            .role(MemberRole.PARTICIPANT)
            .joinedAt(NOW)
            .build());
    open(saveInvite(joined), me, InviteeStatus.PENDING);
    open(saveInvite(saveCapsule(CapsuleStatus.WRITING, null)), friend, InviteeStatus.PENDING);

    assertThat(capsuleInviteeRepository.findPendingFor(me.getId(), NOW)).isEmpty();
    assertThat(capsuleInviteeRepository.existsPendingFor(me.getId(), NOW)).isFalse();
  }

  private List<String> tokens(List<ReceivedInvite> received) {
    return received.stream().map(ReceivedInvite::inviteToken).toList();
  }

  private TimeCapsule saveCapsule(CapsuleStatus status, LocalDateTime deletedAt) {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(friend)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .letterDeadlineAt(NOW.plusDays(2))
            .openAt(NOW.plusDays(12))
            .deletedAt(deletedAt)
            .build());
  }

  private CapsuleInvite saveInvite(TimeCapsule capsule) {
    return saveInvite(capsule, MemberRole.PARTICIPANT, InviteStatus.ACTIVE, null);
  }

  private CapsuleInvite saveInvite(
      TimeCapsule capsule, MemberRole role, InviteStatus status, LocalDateTime expiresAt) {
    return capsuleInviteRepository.save(
        CapsuleInvite.builder()
            .timeCapsule(capsule)
            .inviter(friend)
            .inviteToken(UUID.randomUUID().toString())
            .targetRole(role)
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

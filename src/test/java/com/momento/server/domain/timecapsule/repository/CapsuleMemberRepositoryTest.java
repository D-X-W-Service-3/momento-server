package com.momento.server.domain.timecapsule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class CapsuleMemberRepositoryTest {

  private static final LocalDateTime FIRST = LocalDateTime.of(2027, 12, 12, 9, 12, 30);

  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private CapsuleMember me;
  private CapsuleMember other;

  @BeforeEach
  void setUp() {
    cleanUp();
    User owner = userRepository.save(User.builder().kakaoId("open-owner").nickname("상래").build());
    User friend = userRepository.save(User.builder().kakaoId("open-friend").nickname("유빈").build());
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            TimeCapsule.builder()
                .creator(owner)
                .iconType("ICE_CREAM")
                .title("캡슐")
                .capsuleType(CapsuleType.GROUP)
                .visibilityType(VisibilityType.ALL_MEMBERS)
                .status(CapsuleStatus.OPENED)
                .letterDeadlineAt(LocalDateTime.now().plusDays(10))
                .openAt(LocalDateTime.now().plusDays(30))
                .build());
    me = saveMember(capsule, owner, MemberRole.OWNER);
    other = saveMember(capsule, friend, MemberRole.PARTICIPANT);
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("확인한 적 없는 멤버에게는 개봉 확인 시각을 남기고 1행을 돌려준다")
  void marksFirstOpen() {
    int updated = markOpened(me, FIRST);

    assertThat(updated).isEqualTo(1);
    assertThat(openedAtOf(me)).isEqualTo(FIRST);
  }

  @Test
  @DisplayName("이미 확인한 멤버는 다시 기록해도 처음 시각이 그대로이고 0행을 돌려준다")
  void keepsFirstOpenedAt() {
    markOpened(me, FIRST);

    int updated = markOpened(me, FIRST.plusHours(1));

    assertThat(updated).isZero();
    assertThat(openedAtOf(me)).isEqualTo(FIRST);
  }

  @Test
  @DisplayName("한 멤버의 확인은 같은 캡슐의 다른 멤버에게 영향을 주지 않는다")
  void doesNotTouchOtherMembers() {
    markOpened(me, FIRST);

    assertThat(openedAtOf(other)).isNull();
  }

  private int markOpened(CapsuleMember member, LocalDateTime now) {
    return transactionTemplate.execute(
        tx -> capsuleMemberRepository.markOpenedIfAbsent(member.getId(), now));
  }

  private LocalDateTime openedAtOf(CapsuleMember member) {
    return capsuleMemberRepository.findById(member.getId()).orElseThrow().getOpenedAt();
  }

  private CapsuleMember saveMember(TimeCapsule capsule, User user, MemberRole role) {
    return capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(role)
            .joinedAt(LocalDateTime.now())
            .build());
  }

  private void cleanUp() {
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}

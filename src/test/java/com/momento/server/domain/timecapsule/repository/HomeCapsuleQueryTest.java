package com.momento.server.domain.timecapsule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;

/**
 * 홈 "가장 먼저 열리는 캡슐" 쿼리를 검증한다.
 *
 * <p>캡슐 일시는 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌면, 지난 일시의 캡슐 상태를 바꿀 수 있다. 상태는 테스트마다 직접 정한다.
 */
@SpringBootTest
class HomeCapsuleQueryTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2045, 1, 10, 12, 0);
  private static final Limit ALL = Limit.of(10);

  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private UserRepository userRepository;

  private User me;
  private User friend;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = saveUser("home-capsule-me", "나");
    friend = saveUser("home-capsule-friend", "친구");
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("작성 중 · 잠긴 캡슐을 개봉일 순으로, 같으면 ID 순으로 준다")
  void writingAndLockedByOpenAt() {
    TimeCapsule later = capsule(me, CapsuleStatus.WRITING, NOW.plusDays(20));
    TimeCapsule locked = capsule(me, CapsuleStatus.LOCKED, NOW.plusDays(5));
    TimeCapsule sameDay = capsule(me, CapsuleStatus.WRITING, NOW.plusDays(5));
    capsule(me, CapsuleStatus.OPENED, NOW.minusDays(1));

    assertThat(ids(timeCapsuleRepository.findMyNextCapsules(me.getId(), ALL)))
        .containsExactly(locked.getId(), sameDay.getId(), later.getId());
  }

  @Test
  @DisplayName("삭제된 캡슐, 내보내진 캡슐, 남의 캡슐은 빠진다")
  void onlyMyActiveCapsules() {
    TimeCapsule mine = capsule(me, CapsuleStatus.WRITING, NOW.plusDays(30));
    TimeCapsule deleted = capsule(me, CapsuleStatus.WRITING, NOW.plusDays(1));
    deleted.delete(NOW);
    timeCapsuleRepository.save(deleted);
    TimeCapsule removed = capsule(friend, CapsuleStatus.WRITING, NOW.plusDays(2));
    join(removed, me, MemberStatus.REMOVED);
    capsule(friend, CapsuleStatus.WRITING, NOW.plusDays(3));

    assertThat(ids(timeCapsuleRepository.findMyNextCapsules(me.getId(), ALL)))
        .containsExactly(mine.getId());
  }

  @Test
  @DisplayName("개수 제한만큼만 읽는다")
  void respectsLimit() {
    TimeCapsule first = capsule(me, CapsuleStatus.WRITING, NOW.plusDays(1));
    capsule(me, CapsuleStatus.WRITING, NOW.plusDays(2));

    assertThat(ids(timeCapsuleRepository.findMyNextCapsules(me.getId(), Limit.of(1))))
        .containsExactly(first.getId());
  }

  private List<Long> ids(List<TimeCapsule> capsules) {
    return capsules.stream().map(TimeCapsule::getId).toList();
  }

  private TimeCapsule capsule(User owner, CapsuleStatus status, LocalDateTime openAt) {
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            TimeCapsule.builder()
                .creator(owner)
                .iconType("ICE_CREAM")
                .title("캡슐")
                .capsuleType(CapsuleType.GROUP)
                .visibilityType(VisibilityType.ALL_MEMBERS)
                .status(status)
                .letterDeadlineAt(openAt.minusDays(2))
                .openAt(openAt)
                .build());
    join(capsule, owner, MemberStatus.ACTIVE);
    return capsule;
  }

  private void join(TimeCapsule capsule, User user, MemberStatus status) {
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(capsule.getCreator() == user ? MemberRole.OWNER : MemberRole.PARTICIPANT)
            .status(status)
            .joinedAt(NOW.minusDays(30))
            .build());
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  private void cleanUp() {
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}

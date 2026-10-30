package com.momento.server.domain.timecapsule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.letter.repository.LetterRepository;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 멤버 목록 · 내보내기 쿼리를 검증한다.
 *
 * <p>캡슐 일시는 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌면, 지난 일시의 캡슐 상태를 바꿀 수 있다.
 */
@SpringBootTest
class CapsuleMemberManageQueryTest {

  private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2045, 1, 1, 0, 0);
  private static final LocalDateTime NOW = LocalDateTime.of(2044, 6, 1, 12, 0);
  private static final List<MemberRole> PARTICIPANTS =
      List.of(MemberRole.OWNER, MemberRole.PARTICIPANT);

  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private LetterRepository letterRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private User owner;
  private TimeCapsule capsule;
  private TimeCapsule otherCapsule;

  @BeforeEach
  void setUp() {
    cleanUp();
    owner = saveUser("manage-owner", "방장");
    capsule = saveCapsule();
    otherCapsule = saveCapsule();
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("목록은 방장을 맨 앞에 두고 참여한 순서다. 수신자 · 내보낸 멤버 · 다른 캡슐 멤버는 빠진다")
  void listsOwnerFirstThenJoinOrder() {
    join(capsule, saveUser("manage-late", "나중"), MemberRole.PARTICIPANT, MemberStatus.ACTIVE, 1);
    join(capsule, owner, MemberRole.OWNER, MemberStatus.ACTIVE, 2);
    join(capsule, saveUser("manage-early", "먼저"), MemberRole.PARTICIPANT, MemberStatus.ACTIVE, 3);
    join(capsule, saveUser("manage-r", "수신"), MemberRole.RECIPIENT, MemberStatus.ACTIVE, 0);
    join(capsule, saveUser("manage-x", "내보냄"), MemberRole.PARTICIPANT, MemberStatus.REMOVED, 0);
    join(otherCapsule, saveUser("manage-o", "남"), MemberRole.PARTICIPANT, MemberStatus.ACTIVE, 9);

    List<CapsuleMember> members =
        capsuleMemberRepository.findActiveWithUser(capsule.getId(), PARTICIPANTS);

    // 트랜잭션 밖에서 회원 닉네임을 읽는다. fetch join 이 아니면 지연 로딩 예외가 난다.
    assertThat(members)
        .extracting(m -> m.getUser().getNickname())
        .containsExactly("방장", "먼저", "나중");
    assertThat(
            capsuleMemberRepository.findActiveWithUser(
                capsule.getId(), List.of(MemberRole.RECIPIENT)))
        .extracting(m -> m.getUser().getNickname())
        .containsExactly("수신");
  }

  @Test
  @DisplayName("내보낼 대상은 이 캡슐의 ACTIVE 멤버만 찾는다")
  void findsTargetOnlyInThisCapsule() {
    CapsuleMember active =
        join(capsule, saveUser("manage-a", "가"), MemberRole.PARTICIPANT, MemberStatus.ACTIVE, 0);
    CapsuleMember removed =
        join(capsule, saveUser("manage-b", "나"), MemberRole.PARTICIPANT, MemberStatus.REMOVED, 0);
    CapsuleMember elsewhere =
        join(
            otherCapsule,
            saveUser("manage-c", "다"),
            MemberRole.PARTICIPANT,
            MemberStatus.ACTIVE,
            0);

    assertThat(find(active)).isTrue();
    assertThat(find(removed)).isFalse();
    assertThat(find(elsewhere)).isFalse();
  }

  @Test
  @DisplayName("대상이 이 캡슐에 쓴 편지만 지우고 deletedAt · updatedAt 을 같은 시각으로 채운다")
  void softDeletesOnlyAuthorLettersInCapsule() {
    User target = saveUser("manage-target", "대상");
    Letter draft = saveLetter(capsule, target, LetterStatus.DRAFT, null);
    Letter alreadyDeleted = saveLetter(capsule, target, LetterStatus.SUBMITTED, FAR_FUTURE);
    Letter ownersLetter = saveLetter(capsule, owner, LetterStatus.SUBMITTED, null);
    Letter otherCapsuleLetter = saveLetter(otherCapsule, target, LetterStatus.SUBMITTED, null);

    Integer deleted =
        transactionTemplate.execute(
            status -> letterRepository.softDeleteByAuthor(capsule.getId(), target.getId(), NOW));

    assertThat(deleted).isEqualTo(1);
    Letter reloaded = letterRepository.findById(draft.getId()).orElseThrow();
    assertThat(reloaded.getDeletedAt()).isEqualTo(NOW);
    assertThat(reloaded.getUpdatedAt()).isEqualTo(NOW);
    assertThat(letterRepository.findById(alreadyDeleted.getId()).orElseThrow().getDeletedAt())
        .isEqualTo(FAR_FUTURE);
    assertThat(letterRepository.findById(ownersLetter.getId()).orElseThrow().getDeletedAt())
        .isNull();
    assertThat(letterRepository.findById(otherCapsuleLetter.getId()).orElseThrow().getDeletedAt())
        .isNull();
  }

  private boolean find(CapsuleMember member) {
    return capsuleMemberRepository
        .findByIdAndTimeCapsuleIdAndStatus(member.getId(), capsule.getId(), MemberStatus.ACTIVE)
        .isPresent();
  }

  private TimeCapsule saveCapsule() {
    return timeCapsuleRepository.save(
        TimeCapsule.builder()
            .creator(owner)
            .iconType("ICE_CREAM")
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(CapsuleStatus.WRITING)
            .letterDeadlineAt(FAR_FUTURE.minusDays(10))
            .openAt(FAR_FUTURE)
            .build());
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  /** {@code joinedDaysAgo} 가 클수록 먼저 참여했다. */
  private CapsuleMember join(
      TimeCapsule target, User user, MemberRole role, MemberStatus status, int joinedDaysAgo) {
    return capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(target)
            .user(user)
            .role(role)
            .status(status)
            .joinedAt(FAR_FUTURE.minusDays(100 + joinedDaysAgo))
            .build());
  }

  private Letter saveLetter(
      TimeCapsule target, User author, LetterStatus status, LocalDateTime deletedAt) {
    return letterRepository.save(
        Letter.builder()
            .timeCapsule(target)
            .author(author)
            .content("편지")
            .status(status)
            .deletedAt(deletedAt)
            .build());
  }

  private void cleanUp() {
    letterRepository.deleteAllInBatch();
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}

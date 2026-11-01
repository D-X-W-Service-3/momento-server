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
import org.springframework.data.domain.Limit;

/**
 * 상세 조회용 미리보기 · 편지 수 쿼리를 검증한다.
 *
 * <p>캡슐 일시는 먼 미래로 둔다. 상태 전이 스케줄러가 테스트 중에도 돌면, 지난 일시의 캡슐 상태를 바꿀 수 있다.
 */
@SpringBootTest
class CapsuleDetailQueryTest {

  private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2045, 1, 1, 0, 0);
  private static final List<MemberRole> PARTICIPANTS =
      List.of(MemberRole.OWNER, MemberRole.PARTICIPANT);

  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;
  @Autowired private LetterRepository letterRepository;
  @Autowired private UserRepository userRepository;

  private User owner;
  private TimeCapsule capsule;

  @BeforeEach
  void setUp() {
    cleanUp();
    owner = saveUser("detail-owner", "방장");
    capsule =
        timeCapsuleRepository.save(
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

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("미리보기는 방장을 맨 앞에 두고, 나머지는 참여한 순서다. 수신자와 내보낸 멤버는 빠진다")
  void previewsOwnerFirstThenJoinOrder() {
    User early = saveUser("detail-early", "먼저");
    User late = saveUser("detail-late", "나중");
    join(late, MemberRole.PARTICIPANT, MemberStatus.ACTIVE, 1);
    join(owner, MemberRole.OWNER, MemberStatus.ACTIVE, 2);
    join(early, MemberRole.PARTICIPANT, MemberStatus.ACTIVE, 3);
    join(saveUser("detail-recipient", "수신"), MemberRole.RECIPIENT, MemberStatus.ACTIVE, 0);
    join(saveUser("detail-removed", "내보냄"), MemberRole.PARTICIPANT, MemberStatus.REMOVED, 0);

    assertThat(capsuleMemberRepository.findPreviews(capsule.getId(), PARTICIPANTS, Limit.of(5)))
        .extracting(MemberPreview::nickname)
        .containsExactly("방장", "먼저", "나중");
  }

  @Test
  @DisplayName("미리보기는 정한 인원까지만 읽고, 인원 수는 역할별로 따로 센다")
  void limitsPreviewsAndCountsByRole() {
    join(owner, MemberRole.OWNER, MemberStatus.ACTIVE, 0);
    for (int i = 0; i < 6; i++) {
      join(saveUser("detail-p" + i, "참여" + i), MemberRole.PARTICIPANT, MemberStatus.ACTIVE, i);
    }
    join(saveUser("detail-r", "수신"), MemberRole.RECIPIENT, MemberStatus.ACTIVE, 0);

    assertThat(capsuleMemberRepository.findPreviews(capsule.getId(), PARTICIPANTS, Limit.of(5)))
        .hasSize(5);
    assertThat(
            capsuleMemberRepository.countByTimeCapsuleIdAndRoleInAndStatus(
                capsule.getId(), PARTICIPANTS, MemberStatus.ACTIVE))
        .isEqualTo(7);
    assertThat(
            capsuleMemberRepository.countByTimeCapsuleIdAndRoleInAndStatus(
                capsule.getId(), List.of(MemberRole.RECIPIENT), MemberStatus.ACTIVE))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("삭제되지 않은 편지는 임시 저장까지 세고, 삭제된 편지는 빼고 센다")
  void countsUndeletedLettersIncludingDrafts() {
    saveLetter(owner, LetterStatus.DRAFT, null);
    saveLetter(saveUser("detail-a", "가"), LetterStatus.SUBMITTED, null);
    saveLetter(saveUser("detail-b", "나"), LetterStatus.SUBMITTED, FAR_FUTURE);

    assertThat(letterRepository.countByTimeCapsuleIdAndDeletedAtIsNull(capsule.getId()))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("임시 저장도 내 편지로 보고, 삭제한 편지는 내 편지로 보지 않는다")
  void myLetterExists() {
    User drafted = saveUser("detail-draft", "임시");
    User deleted = saveUser("detail-deleted", "삭제");
    saveLetter(drafted, LetterStatus.DRAFT, null);
    saveLetter(deleted, LetterStatus.SUBMITTED, FAR_FUTURE);

    assertThat(
            letterRepository.existsByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(
                capsule.getId(), drafted.getId()))
        .isTrue();
    assertThat(
            letterRepository.existsByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(
                capsule.getId(), deleted.getId()))
        .isFalse();
    assertThat(
            letterRepository.existsByTimeCapsuleIdAndAuthorIdAndDeletedAtIsNull(
                capsule.getId(), owner.getId()))
        .isFalse();
  }

  private User saveUser(String kakaoId, String nickname) {
    return userRepository.save(User.builder().kakaoId(kakaoId).nickname(nickname).build());
  }

  /** {@code joinedDaysAgo} 가 클수록 먼저 참여했다. */
  private void join(User user, MemberRole role, MemberStatus status, int joinedDaysAgo) {
    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(user)
            .role(role)
            .status(status)
            .joinedAt(FAR_FUTURE.minusDays(100 + joinedDaysAgo))
            .build());
  }

  private void saveLetter(User author, LetterStatus status, LocalDateTime deletedAt) {
    letterRepository.save(
        Letter.builder()
            .timeCapsule(capsule)
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

package com.momento.server.domain.timecapsule.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 편지 한 통을 누가 볼 수 있는지. 편지 목록 API 가 이 판정으로 편지를 거른다. */
class TimeCapsuleTest {

  @ParameterizedTest(name = "{0} 캡슐의 {1}, 내가 쓴 편지={2} → {3}")
  @CsvSource({
    // 수신자 + 작성자: 수신자는 전부, 작성자는 자기 편지만
    "RECIPIENT_AND_AUTHOR, RECIPIENT,   false, true",
    "RECIPIENT_AND_AUTHOR, PARTICIPANT, true,  true",
    "RECIPIENT_AND_AUTHOR, PARTICIPANT, false, false",
    "RECIPIENT_AND_AUTHOR, OWNER,       true,  true",
    "RECIPIENT_AND_AUTHOR, OWNER,       false, false",
    // 전원: 누구나 모든 편지
    "ALL_MEMBERS,       RECIPIENT,   false, true",
    "ALL_MEMBERS,       PARTICIPANT, false, true",
    "ALL_MEMBERS,       OWNER,       false, true"
  })
  @DisplayName("열린 캡슐에서 편지 한 통을 볼 수 있는지는 공개 범위, 역할, 내가 쓴 편지인지로 정해진다")
  void letterVisibility(
      VisibilityType visibility, MemberRole role, boolean isAuthor, boolean expected) {
    TimeCapsule capsule = capsule(CapsuleStatus.OPENED, visibility);

    assertThat(capsule.canViewLetter(role, isAuthor)).isEqualTo(expected);
  }

  @Test
  @DisplayName("열리기 전에는 누구도 편지를 볼 수 없다")
  void noOneSeesLettersBeforeOpening() {
    TimeCapsule locked = capsule(CapsuleStatus.LOCKED, VisibilityType.ALL_MEMBERS);

    assertThat(locked.canViewLetters(MemberRole.RECIPIENT)).isFalse();
    assertThat(locked.canViewLetter(MemberRole.RECIPIENT, false)).isFalse();
    assertThat(locked.canViewLetter(MemberRole.OWNER, true)).isFalse();
  }

  @ParameterizedTest(name = "{0}, 마감까지 {1}초 → {2}")
  @CsvSource({
    "WRITING, 1,  true",
    "WRITING, 0,  false",
    "WRITING, -1, false",
    "LOCKED,  1,  false",
    "OPENED,  1,  false"
  })
  @DisplayName("WRITING 이고 마감 시각 전에만 캡슐을 고치거나 지울 수 있다. 스케줄러가 돌기 전이라도 마감이 지나면 안 된다")
  void editableOnlyBeforeDeadline(CapsuleStatus status, long secondsLeft, boolean expected) {
    LocalDateTime deadline = LocalDateTime.of(2045, 1, 1, 23, 59, 59);
    TimeCapsule capsule =
        TimeCapsule.builder()
            .iconType("ICE_CREAM")
            .title("캡슐")
            .capsuleType(CapsuleType.GROUP)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .status(status)
            .letterDeadlineAt(deadline)
            .openAt(deadline.plusDays(10))
            .build();

    assertThat(capsule.isEditable(deadline.minusSeconds(secondsLeft))).isEqualTo(expected);
  }

  @Test
  @DisplayName("설명에 null 을 넣으면 지운다")
  void clearsDescription() {
    TimeCapsule capsule = capsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS);
    capsule.updateDescription("설명");

    capsule.updateDescription(null);

    assertThat(capsule.getDescription()).isNull();
  }

  @Test
  @DisplayName("삭제하면 삭제 시각이 남는다")
  void deleteRecordsTime() {
    TimeCapsule capsule = capsule(CapsuleStatus.WRITING, VisibilityType.ALL_MEMBERS);
    LocalDateTime now = LocalDateTime.of(2045, 1, 1, 9, 0);

    capsule.delete(now);

    assertThat(capsule.getDeletedAt()).isEqualTo(now);
  }

  @ParameterizedTest
  @CsvSource({
    "WRITING,1,60,true", "WRITING,0,60,false", "WRITING,-1,60,false",
    "WRITING,60,1,true", "WRITING,60,0,false", "WRITING,60,-1,false",
    "LOCKED,60,60,false", "OPENED,60,60,false"
  })
  void letterPeriodChecksStatusDeadlineAndOpening(
      CapsuleStatus status, int openOffset, int deadlineOffset, boolean expected) {
    LocalDateTime now = LocalDateTime.of(2027, 1, 1, 12, 0);
    TimeCapsule capsule =
        TimeCapsule.builder()
            .status(status)
            .visibilityType(VisibilityType.ALL_MEMBERS)
            .openAt(now.plusSeconds(openOffset))
            .letterDeadlineAt(now.plusSeconds(deadlineOffset))
            .build();
    assertThat(capsule.isLetterWritingOpen(now)).isEqualTo(expected);
    for (MemberRole role : MemberRole.values()) {
      assertThat(capsule.getLetterWritingEligibility(role, now))
          .isEqualTo(expected ? LetterWritingEligibility.ALLOWED : LetterWritingEligibility.CLOSED);
    }
  }

  private TimeCapsule capsule(CapsuleStatus status, VisibilityType visibility) {
    return TimeCapsule.builder()
        .iconType("ICE_CREAM")
        .title("캡슐")
        .capsuleType(CapsuleType.GROUP)
        .visibilityType(visibility)
        .status(status)
        .letterDeadlineAt(LocalDateTime.now().minusDays(1))
        .openAt(LocalDateTime.now())
        .build();
  }
}

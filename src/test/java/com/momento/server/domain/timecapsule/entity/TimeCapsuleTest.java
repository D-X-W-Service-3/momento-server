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
    // 수신자만: 작성자는 OWNER 라도 자기 편지를 못 본다
    "RECIPIENT_ONLY,    RECIPIENT,   false, true",
    "RECIPIENT_ONLY,    PARTICIPANT, true,  false",
    "RECIPIENT_ONLY,    OWNER,       true,  false",
    // 수신자 + 작성자: 수신자는 전부, 작성자는 자기 편지만
    "PARTICIPANTS_ONLY, RECIPIENT,   false, true",
    "PARTICIPANTS_ONLY, PARTICIPANT, true,  true",
    "PARTICIPANTS_ONLY, PARTICIPANT, false, false",
    "PARTICIPANTS_ONLY, OWNER,       true,  true",
    "PARTICIPANTS_ONLY, OWNER,       false, false",
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

  private TimeCapsule capsule(CapsuleStatus status, VisibilityType visibility) {
    return TimeCapsule.builder()
        .title("캡슐")
        .capsuleType(CapsuleType.GROUP)
        .visibilityType(visibility)
        .status(status)
        .openAt(LocalDateTime.now())
        .build();
  }
}

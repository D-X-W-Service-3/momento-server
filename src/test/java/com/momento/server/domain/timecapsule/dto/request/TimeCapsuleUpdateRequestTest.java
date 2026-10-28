package com.momento.server.domain.timecapsule.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TimeCapsuleUpdateRequestTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("아무 필드도 보내지 않으면 검증을 통과한다")
  void emptyRequestIsValid() {
    assertThat(messages(new TimeCapsuleUpdateRequest(null, null, null))).isEmpty();
  }

  @Test
  @DisplayName("문자열 필드는 앞뒤 공백을 뺀다")
  void stripsStrings() {
    TimeCapsuleUpdateRequest request = new TimeCapsuleUpdateRequest(" ICE_CREAM ", " 제주 ", " 설명 ");

    assertThat(request.iconType()).isEqualTo("ICE_CREAM");
    assertThat(request.title()).isEqualTo("제주");
    assertThat(request.description()).isEqualTo("설명");
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"", "   "})
  @DisplayName("아이콘 · 이름은 비울 수 없다")
  void rejectsBlankIconAndTitle(String blank) {
    assertThat(messages(new TimeCapsuleUpdateRequest(blank, blank, null)))
        .containsExactlyInAnyOrder("캡슐 아이콘은 1자 이상 30자 이하여야 합니다.", "타임캡슐 이름은 1자 이상 20자 이하여야 합니다.");
  }

  @Test
  @DisplayName("설명의 빈 문자열은 지우라는 뜻이라 통과한다")
  void blankDescriptionMeansClear() {
    TimeCapsuleUpdateRequest request = new TimeCapsuleUpdateRequest(null, null, "  ");

    assertThat(request.description()).isEmpty();
    assertThat(messages(request)).isEmpty();
  }

  @Test
  @DisplayName("글자 수는 생성과 같다: 아이콘 30 · 이름 20 · 설명 45")
  void lengthLimits() {
    assertThat(
            messages(new TimeCapsuleUpdateRequest("a".repeat(30), "가".repeat(20), "나".repeat(45))))
        .isEmpty();
    assertThat(
            messages(new TimeCapsuleUpdateRequest("a".repeat(31), "가".repeat(21), "나".repeat(46))))
        .containsExactlyInAnyOrder(
            "캡슐 아이콘은 1자 이상 30자 이하여야 합니다.",
            "타임캡슐 이름은 1자 이상 20자 이하여야 합니다.",
            "타임캡슐 설명은 45자 이하여야 합니다.");
  }

  private List<String> messages(TimeCapsuleUpdateRequest request) {
    return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
  }
}

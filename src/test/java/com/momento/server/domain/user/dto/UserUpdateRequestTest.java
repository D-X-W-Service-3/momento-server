package com.momento.server.domain.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.user.dto.request.UserUpdateRequest;
import com.momento.server.domain.user.dto.response.MyProfileResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserUpdateRequestTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("아무 필드도 보내지 않으면 검증을 통과한다")
  void emptyRequestIsValid() {
    assertThat(validate(new UserUpdateRequest(null, null, null, null))).isEmpty();
  }

  @Test
  @DisplayName("문자열 필드는 앞뒤 공백을 뺀다")
  void stripsStrings() {
    UserUpdateRequest request =
        new UserUpdateRequest("  상래  ", null, " a@b.com ", " 010-1234-5678 ");

    assertThat(request.nickname()).isEqualTo("상래");
    assertThat(request.email()).isEqualTo("a@b.com");
    assertThat(request.phoneNumber()).isEqualTo("010-1234-5678");
    assertThat(validate(request)).isEmpty();
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"", "  ", "상", " 상 ", "가나다라마바사아자차카타파하가나다라마바사"})
  @DisplayName("닉네임이 공백을 뺀 뒤 2~20자가 아니면 실패한다")
  void rejectsNicknameLength(String nickname) {
    assertThat(messages(new UserUpdateRequest(nickname, null, null, null)))
        .containsExactly(UserUpdateRequest.NICKNAME_LENGTH_MESSAGE);
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"상래", "가나다라마바사아자차카타파하가나다라마바"})
  @DisplayName("닉네임 2자와 20자는 통과한다")
  void acceptsNicknameBoundaries(String nickname) {
    assertThat(validate(new UserUpdateRequest(nickname, null, null, null))).isEmpty();
  }

  @Test
  @DisplayName("이메일 · 전화번호의 빈 문자열은 지우라는 뜻이라 통과한다")
  void blankEmailAndPhoneMeanClear() {
    UserUpdateRequest request = new UserUpdateRequest(null, null, "  ", "");

    assertThat(request.email()).isEmpty();
    assertThat(request.phoneNumber()).isEmpty();
    assertThat(validate(request)).isEmpty();
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"sangrae", "sangrae@", "@example.com"})
  @DisplayName("이메일 형식이 아니면 실패한다")
  void rejectsInvalidEmail(String email) {
    assertThat(messages(new UserUpdateRequest(null, null, email, null)))
        .containsExactly("이메일 형식이 올바르지 않습니다.");
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"01012345678", "011-1234-5678", "010-123-5678", "010-1234-567a"})
  @DisplayName("전화번호가 010-1234-5678 형식이 아니면 실패한다")
  void rejectsInvalidPhoneNumber(String phoneNumber) {
    assertThat(messages(new UserUpdateRequest(null, null, null, phoneNumber)))
        .containsExactly("전화번호는 010-1234-5678 형식이어야 합니다.");
  }

  @Test
  @DisplayName("미래 날짜는 생일로 받지 않는다")
  void rejectsFutureBirthday() {
    assertThat(messages(new UserUpdateRequest(null, LocalDate.now().plusDays(1), null, null)))
        .containsExactly("생일은 오늘 이전이어야 합니다.");
  }

  @Test
  @DisplayName("활동 요약은 공개 전 = 작성 중 + 잠김, 전체 = 공개 전 + 공개됨 이고 없는 상태는 0 이다")
  void activityCounts() {
    MyProfileResponse.Activity activity =
        MyProfileResponse.Activity.of(Map.of(CapsuleStatus.WRITING, 2L, CapsuleStatus.OPENED, 3L));

    assertThat(activity.beforeOpenCapsuleCount()).isEqualTo(2);
    assertThat(activity.openedCapsuleCount()).isEqualTo(3);
    assertThat(activity.totalCapsuleCount()).isEqualTo(5);
  }

  private Set<ConstraintViolation<UserUpdateRequest>> validate(UserUpdateRequest request) {
    return validator.validate(request);
  }

  private List<String> messages(UserUpdateRequest request) {
    return validate(request).stream().map(ConstraintViolation::getMessage).toList();
  }
}

package com.momento.server.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 닉네임 사용 가능 여부 확인 요청. 쿼리 파라미터를 {@code @ModelAttribute} 로 받는다.
 *
 * <p>{@code @RequestParam} 에 제약을 걸면 처리기가 없는 {@code HandlerMethodValidationException} 이 나서 500 이 된다.
 * 레코드로 받으면 기존 {@code MethodArgumentNotValidException} 처리기가 필드 메시지로 400 을 낸다. 길이 규칙은 내 정보 수정과 같다.
 */
@Schema(description = "닉네임 사용 가능 여부 확인 요청")
public record NicknameAvailabilityRequest(
    @Schema(description = "확인할 닉네임. 앞뒤 공백을 빼고 2~20자.", example = "상래")
        @NotNull(message = "닉네임을 입력해 주세요.")
        @Size(
            min = UserUpdateRequest.NICKNAME_MIN_LENGTH,
            max = UserUpdateRequest.NICKNAME_MAX_LENGTH,
            message = UserUpdateRequest.NICKNAME_LENGTH_MESSAGE)
        String nickname) {

  public NicknameAvailabilityRequest {
    nickname = nickname == null ? null : nickname.strip();
  }
}

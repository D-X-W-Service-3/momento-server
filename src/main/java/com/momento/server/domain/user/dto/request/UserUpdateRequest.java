package com.momento.server.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 내 정보 수정 요청. 보낸 필드만 바꾸고, 보내지 않았거나 null 인 필드는 그대로 둔다.
 *
 * <p>문자열은 검증 전에 앞뒤 공백을 뺀다. 이메일 · 전화번호는 빈 문자열을 보내면 지운다. 생일은 지울 수 없다.
 */
@Schema(description = "내 정보 수정 요청. 보낸 필드만 바꾼다.")
public record UserUpdateRequest(
    @Schema(description = "닉네임. 앞뒤 공백을 빼고 2~20자. 다른 회원과 겹치면 409 다.", example = "상래")
        @Size(
            min = NICKNAME_MIN_LENGTH,
            max = NICKNAME_MAX_LENGTH,
            message = NICKNAME_LENGTH_MESSAGE)
        String nickname,
    @Schema(description = "생일. 바꿀 수만 있고 지울 수 없다.", example = "2001-03-14")
        @PastOrPresent(message = "생일은 오늘 이전이어야 합니다.")
        LocalDate birthday,
    @Schema(description = "이메일. 빈 문자열을 보내면 지운다.", example = "sangrae@example.com")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email,
    @Schema(description = "전화번호. 010-1234-5678 형식. 빈 문자열을 보내면 지운다.", example = "010-1234-5678")
        @Pattern(regexp = "^$|^010-\\d{4}-\\d{4}$", message = "전화번호는 010-1234-5678 형식이어야 합니다.")
        String phoneNumber) {

  public static final int NICKNAME_MIN_LENGTH = 2;
  public static final int NICKNAME_MAX_LENGTH = 20;
  public static final String NICKNAME_LENGTH_MESSAGE = "닉네임은 2자 이상 20자 이하여야 합니다.";

  public UserUpdateRequest {
    nickname = strip(nickname);
    email = strip(email);
    phoneNumber = strip(phoneNumber);
  }

  private static String strip(String value) {
    return value == null ? null : value.strip();
  }
}

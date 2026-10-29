package com.momento.server.domain.timecapsule.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 타임캡슐 수정 요청. 보낸 필드만 바꾸고, 보내지 않았거나 null 인 필드는 그대로 둔다.
 *
 * <p>문자열은 검증 전에 앞뒤 공백을 뺀다. 아이콘 · 이름은 비울 수 없고, 설명은 빈 문자열을 보내면 지운다. 글자 수는 생성과 같다.
 */
@Schema(description = "타임캡슐 수정 요청. 보낸 필드만 바꾼다.")
public record TimeCapsuleUpdateRequest(
    @Schema(description = "캡슐 대표 아이콘. 비울 수 없다.", example = "ICE_CREAM")
        @Size(min = 1, max = 30, message = "캡슐 아이콘은 1자 이상 30자 이하여야 합니다.")
        String iconType,
    @Schema(description = "타임캡슐 이름. 비울 수 없고 20자 이하.", example = "2024 우리, 다시 만나는 날")
        @Size(min = 1, max = 20, message = "타임캡슐 이름은 1자 이상 20자 이하여야 합니다.")
        String title,
    @Schema(description = "타임캡슐 설명. 45자 이하. 빈 문자열을 보내면 지운다.", example = "1년 동안 서로에게 보낸 응원")
        @Size(max = 45, message = "타임캡슐 설명은 45자 이하여야 합니다.")
        String description) {

  public TimeCapsuleUpdateRequest {
    iconType = strip(iconType);
    title = strip(title);
    description = strip(description);
  }

  private static String strip(String value) {
    return value == null ? null : value.strip();
  }
}

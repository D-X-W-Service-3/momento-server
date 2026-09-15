package com.momento.server.domain.letter.dto.response;

import com.momento.server.domain.letter.entity.Letter;
import com.momento.server.domain.letter.entity.LetterStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내 편지 응답")
public record LetterResponse(
    @Schema(description = "편지 ID", example = "31") Long letterId,
    @Schema(description = "타임캡슐 ID", example = "12") Long capsuleId,
    @Schema(description = "본문") String content,
    @Schema(description = "편지지 테마", example = "WATERCOLOR") String themeType,
    @Schema(description = "작성 상태", example = "DRAFT") LetterStatus status) {
  public static LetterResponse from(Letter letter) {
    return new LetterResponse(
        letter.getId(),
        letter.getTimeCapsule().getId(),
        letter.getContent(),
        letter.getThemeType(),
        letter.getStatus());
  }
}

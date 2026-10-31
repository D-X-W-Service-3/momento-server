package com.momento.server.domain.letter.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "편지 작성 및 즉시 제출 요청")
public record LetterCreateRequest(
    @Schema(description = "편지 제목. 최대 50자다.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "편지 제목은 필수입니다.")
        @Size(max = 50, message = "편지 제목은 50자 이하여야 합니다.")
        String title,
    @Schema(
            description = "편지 본문. 최대 500자다.",
            example = "졸업 정말 축하해!",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "편지 본문은 필수입니다.")
        @Size(max = 500, message = "편지 본문은 500자 이하여야 합니다.")
        String content,
    @Schema(description = "필수 편지지 화풍. 허용값 확정 전에는 최대 20자의 문자열로 받는다.", example = "COZY")
        @NotBlank(message = "편지지 화풍은 필수입니다.")
        @Size(max = 20, message = "편지지 테마는 20자 이하여야 합니다.")
        String themeType) {}

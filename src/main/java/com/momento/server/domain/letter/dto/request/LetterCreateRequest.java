package com.momento.server.domain.letter.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "편지 초안 생성 요청")
public record LetterCreateRequest(
    @Schema(
            description = "본문. 초안이므로 빈 문자열을 허용하며 최대 10000자다.",
            example = "졸업 정말 축하해!",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "편지 본문은 필수입니다.")
        @Size(max = 10000, message = "편지 본문은 10000자 이하여야 합니다.")
        String content,
    @Schema(description = "편지지 테마. 생략 가능하며 허용값 확정 전에는 최대 20자의 문자열로 받는다.", example = "WATERCOLOR")
        @Size(max = 20, message = "편지지 테마는 20자 이하여야 합니다.")
        String themeType) {}

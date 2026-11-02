package com.momento.server.domain.letter.dto.request;

import com.momento.server.domain.letter.entity.LetterThemeType;
import com.momento.server.domain.letter.entity.LetterType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @Schema(
            description = "편지지 화풍",
            example = "WATERCOLOR",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "편지지 화풍은 필수입니다.")
        LetterThemeType themeType,
    @Schema(
            description = "편지 유형. 생일 축하 · 연인과의 기념일 · 크리스마스 · 다른 종류",
            example = "BIRTHDAY",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "편지 유형은 필수입니다.")
        LetterType letterType) {}

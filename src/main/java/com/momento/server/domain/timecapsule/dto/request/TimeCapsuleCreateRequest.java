package com.momento.server.domain.timecapsule.dto.request;

import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Schema(description = "타임캡슐 생성 요청")
public record TimeCapsuleCreateRequest(
    @Schema(
            description = "타임캡슐 제목",
            example = "우리의 졸업 타임캡슐",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "타임캡슐 제목은 필수입니다.")
        @Size(max = 50, message = "타임캡슐 제목은 50자 이하여야 합니다.")
        String title,
    @Schema(description = "타임캡슐 설명", example = "졸업을 기념해서 함께 남기는 편지") String description,
    @Schema(description = "캡슐 유형", example = "GROUP", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "캡슐 유형은 필수입니다.")
        CapsuleType capsuleType,
    @Schema(
            description = "편지 공개 범위",
            example = "ALL_MEMBERS",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "공개 범위는 필수입니다.")
        VisibilityType visibilityType,
    @Schema(
            description = "공개 예정 일시. 현재보다 이후여야 한다.",
            example = "2026-12-25T20:00:00",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "공개 예정 일시는 필수입니다.")
        @Future(message = "공개 예정 일시는 현재 이후여야 합니다.")
        LocalDateTime openAt) {}

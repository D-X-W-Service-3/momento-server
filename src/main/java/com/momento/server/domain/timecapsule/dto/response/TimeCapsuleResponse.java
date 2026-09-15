package com.momento.server.domain.timecapsule.dto.response;

import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "타임캡슐 응답")
public record TimeCapsuleResponse(
    @Schema(description = "타임캡슐 ID", example = "12") Long capsuleId,
    @Schema(description = "제목", example = "우리의 졸업 타임캡슐") String title,
    @Schema(description = "설명", example = "졸업을 기념해서 함께 남기는 편지") String description,
    @Schema(description = "캡슐 유형", example = "GROUP") CapsuleType capsuleType,
    @Schema(description = "편지 공개 범위", example = "ALL_MEMBERS") VisibilityType visibilityType,
    @Schema(description = "캡슐 상태. 생성 직후에는 WRITING 이다.", example = "WRITING") CapsuleStatus status,
    @Schema(description = "공개 예정 일시", example = "2026-12-25T20:00:00") LocalDateTime openAt,
    @Schema(description = "생성 일시", example = "2026-08-14T15:30:00") LocalDateTime createdAt) {

  public static TimeCapsuleResponse from(TimeCapsule capsule) {
    return new TimeCapsuleResponse(
        capsule.getId(),
        capsule.getTitle(),
        capsule.getDescription(),
        capsule.getCapsuleType(),
        capsule.getVisibilityType(),
        capsule.getStatus(),
        capsule.getOpenAt(),
        capsule.getCreatedAt());
  }
}

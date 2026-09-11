package com.momento.server.domain.timecapsule.dto.response;

import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "타임캡슐 상세 응답")
public record TimeCapsuleDetailResponse(
    @Schema(description = "타임캡슐 ID", example = "12") Long capsuleId,
    @Schema(description = "제목", example = "우리의 졸업 타임캡슐") String title,
    @Schema(description = "설명", example = "졸업을 기념해서 함께 남기는 편지") String description,
    @Schema(description = "캡슐 유형", example = "GROUP") CapsuleType capsuleType,
    @Schema(description = "편지 공개 범위", example = "ALL_MEMBERS") VisibilityType visibilityType,
    @Schema(description = "캡슐 상태", example = "WRITING") CapsuleStatus status,
    @Schema(description = "공개 예정 일시", example = "2026-12-25T20:00:00") LocalDateTime openAt,
    @Schema(description = "참여 중인 인원 수. OWNER 를 포함한다.", example = "7") long memberCount,
    @Schema(description = "제출된 편지 수. 임시 저장과 삭제된 편지는 세지 않는다.", example = "5") long letterCount,
    @Schema(description = "이 캡슐에서 내 역할", example = "PARTICIPANT") MemberRole myRole,
    @Schema(description = "참여자 목록을 볼 수 있는지. OWNER 만 true 다.", example = "false")
        boolean canViewMemberList,
    @Schema(description = "편지를 볼 수 있는지. 캡슐이 열렸고 공개 범위에 내 역할이 들어 있을 때 true 다.", example = "false")
        boolean canViewLetters,
    @Schema(description = "생성 일시", example = "2026-08-14T15:30:00") LocalDateTime createdAt) {

  public static TimeCapsuleDetailResponse of(
      TimeCapsule capsule, MemberRole myRole, long memberCount, long letterCount) {
    return new TimeCapsuleDetailResponse(
        capsule.getId(),
        capsule.getTitle(),
        capsule.getDescription(),
        capsule.getCapsuleType(),
        capsule.getVisibilityType(),
        capsule.getStatus(),
        capsule.getOpenAt(),
        memberCount,
        letterCount,
        myRole,
        myRole == MemberRole.OWNER,
        capsule.canViewLetters(myRole),
        capsule.getCreatedAt());
  }
}

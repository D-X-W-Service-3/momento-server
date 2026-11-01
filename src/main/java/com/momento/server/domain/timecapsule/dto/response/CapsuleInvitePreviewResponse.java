package com.momento.server.domain.timecapsule.dto.response;

import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleDetailResponse.Members;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.service.CapsuleInvitePreview;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 초대 링크를 연 회원에게 보여줄 초대 화면. 참여자 · 수신자 미리보기는 캡슐 상세 화면과 같은 모양 · 같은 기준이다. */
@Schema(description = "초대 미리보기 응답")
public record CapsuleInvitePreviewResponse(
    @Schema(description = "초대받은 역할", example = "PARTICIPANT") MemberRole targetRole,
    @Schema(description = "이미 이 캡슐의 멤버인지. true 면 프론트가 캡슐 상세로 보낸다", example = "false")
        boolean alreadyJoined,
    @Schema(description = "캡슐 정보") CapsuleSummary capsule) {

  public static CapsuleInvitePreviewResponse of(CapsuleInvitePreview preview) {
    TimeCapsule capsule = preview.capsule();
    return new CapsuleInvitePreviewResponse(
        preview.invite().getTargetRole(),
        preview.alreadyJoined(),
        new CapsuleSummary(
            capsule.getId(),
            capsule.getIconType(),
            capsule.getTitle(),
            capsule.getDescription(),
            capsule.getCapsuleType(),
            capsule.getStatus(),
            capsule.getOpenAt(),
            preview.memberCount(),
            preview.letterCount(),
            Members.from(preview.participants()),
            Members.from(preview.recipients())));
  }

  @Schema(description = "초대 미리보기용 캡슐 요약")
  public record CapsuleSummary(
      @Schema(description = "캡슐 ID", example = "21") Long capsuleId,
      @Schema(description = "대표 아이콘", example = "ICE_CREAM") String iconType,
      @Schema(description = "캡슐 이름", example = "2024 우리, 다시 만나는 날") String title,
      @Schema(description = "캡슐 설명", nullable = true) String description,
      @Schema(description = "캡슐 유형", example = "GROUP") CapsuleType capsuleType,
      @Schema(description = "캡슐 상태", example = "WRITING") CapsuleStatus status,
      @Schema(description = "개봉 일시", example = "2027-12-12T00:00:00") LocalDateTime openAt,
      @Schema(description = "참여 중인 인원 수. 방장을 포함한다.", example = "5") long memberCount,
      @Schema(description = "제출된 편지 수. 임시 저장과 삭제된 편지는 세지 않는다.", example = "3") long letterCount,
      @Schema(description = "참여자 미리보기. 방장을 포함한다.") Members participants,
      @Schema(description = "수신자 미리보기") Members recipients) {}
}

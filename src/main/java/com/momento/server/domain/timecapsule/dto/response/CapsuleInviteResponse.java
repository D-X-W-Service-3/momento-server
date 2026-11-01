package com.momento.server.domain.timecapsule.dto.response;

import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 초대 링크 발급·재생성 응답. 초대 URL 은 서버가 만들지 않는다 — 클라이언트가 {@code {웹 주소}/invites/{inviteToken}} 로 만든다. */
@Schema(description = "캡슐 초대 링크 응답")
public record CapsuleInviteResponse(
    @Schema(description = "초대 ID", example = "31") Long inviteId,
    @Schema(description = "초대할 역할", example = "PARTICIPANT") MemberRole targetRole,
    @Schema(description = "초대 토큰", example = "b7d3f1e2a9c84d6f") String inviteToken,
    @Schema(description = "만료 일시. 없으면 null.", example = "2026-12-25T19:00:00", nullable = true)
        LocalDateTime expiresAt) {

  public static CapsuleInviteResponse from(CapsuleInvite invite) {
    return new CapsuleInviteResponse(
        invite.getId(), invite.getTargetRole(), invite.getInviteToken(), invite.getExpiresAt());
  }
}

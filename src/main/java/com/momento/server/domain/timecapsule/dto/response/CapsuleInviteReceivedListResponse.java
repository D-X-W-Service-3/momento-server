package com.momento.server.domain.timecapsule.dto.response;

import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "받은 초대 목록 응답")
public record CapsuleInviteReceivedListResponse(
    @Schema(description = "응답 대기 중인 초대 목록(최신순)") List<Item> invites) {

  public static CapsuleInviteReceivedListResponse of(List<Item> invites) {
    return new CapsuleInviteReceivedListResponse(invites);
  }

  @Schema(description = "받은 초대 한 건")
  public record Item(
      @Schema(description = "초대 토큰. 미리보기·수락·거절에 쓴다", example = "b7d3f1e2a9c84d6f")
          String inviteToken,
      @Schema(description = "초대받은 역할", example = "PARTICIPANT") MemberRole targetRole,
      @Schema(description = "초대한 사람 닉네임", example = "유빈") String inviterNickname,
      @Schema(description = "캡슐 ID", example = "21") Long capsuleId,
      @Schema(description = "캡슐 이름", example = "2024 우리, 다시 만나는 날") String capsuleTitle,
      @Schema(description = "개봉 일시", example = "2027-12-12T00:00:00") LocalDateTime openAt) {

    public static Item of(CapsuleInvite invite, TimeCapsule capsule) {
      return new Item(
          invite.getInviteToken(),
          invite.getTargetRole(),
          invite.getInviter().getNickname(),
          capsule.getId(),
          capsule.getTitle(),
          capsule.getOpenAt());
    }
  }
}

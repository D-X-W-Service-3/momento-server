package com.momento.server.domain.timecapsule.controller;

import com.momento.server.domain.timecapsule.dto.response.CapsuleInvitePreviewResponse;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInviteReceivedListResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Invite", description = "초대 링크 미리보기·수락·거절 API")
public interface InviteApi {

  @Operation(
      summary = "초대 미리보기",
      description =
          "링크를 연 회원을 대기 중으로 기록하고(받은 초대 목록 · 탭 홈 배너에 나온다) 초대 화면을 보여준다. 링크 하나를 여러 명이 열 수 있다."
              + " 이미 이 캡슐의 멤버면 기록하지 않고 alreadyJoined 가 true 다. 없는 토큰, 재생성으로 무효가 됐거나 만료된 링크, 삭제된"
              + " 캡슐의 링크, 내가 거절한 링크는 모두 404 INVITE_NOT_FOUND 다.")
  CommonResponse<CapsuleInvitePreviewResponse> getInvite(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "초대 토큰", example = "1a2b3c4d5e6f") String inviteToken);

  @Operation(
      summary = "받은 초대 목록 조회",
      description =
          "링크를 열고 아직 수락 · 거절하지 않은 초대를 최근에 연 순서로 보여준다. 링크가 무효 · 만료됐거나, 캡슐이 잠겼거나 삭제됐거나, 이미 멤버인 초대는"
              + " 빠진다(탭 홈 배너와 같은 조건).")
  CommonResponse<CapsuleInviteReceivedListResponse> getReceivedInvites(
      @Parameter(hidden = true) UserPrincipal principal);

  @Operation(
      summary = "초대 수락",
      description =
          "캡슐에 참여한다. 미리보기 없이 바로 호출해도 된다. 나갔거나 내보내졌던 회원이면 이번 초대가 정한 역할로 다시 참여한다. 이미 참여 중이면"
              + " 409 ALREADY_CAPSULE_MEMBER, 편지 수정 마감 뒤면 409 CAPSULE_NOT_EDITABLE 이다. 링크는 그대로 남아 다른 사람도"
              + " 계속 쓸 수 있다.")
  CommonResponse<?> acceptInvite(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "초대 토큰", example = "1a2b3c4d5e6f") String inviteToken);

  @Operation(
      summary = "초대 거절",
      description =
          "이 링크에 대한 내 응답을 거절로 기록한다. 거절한 링크로는 다시 볼 수도 참여할 수도 없고(404), 방장이 재생성한 새 링크로는 다시 받을 수 있다."
              + " 이미 거절했으면 그대로 성공하고, 이미 이 캡슐의 멤버면 409 ALREADY_CAPSULE_MEMBER 다."
              + " 이 링크로 수락했다가 나가거나 내보내졌으면 수락 기록을 거절로 덮지 않고 409 INVITE_ALREADY_ACCEPTED 다.")
  CommonResponse<?> rejectInvite(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "초대 토큰", example = "1a2b3c4d5e6f") String inviteToken);
}

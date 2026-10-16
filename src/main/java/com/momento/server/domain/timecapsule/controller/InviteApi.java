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
          "로그인한 회원만 호출한다. 이 링크를 처음 여는 회원이면 그 회원에게 고정된다(다른 회원은 이후 이 링크를 쓸 수 없다). 이미 다른"
              + " 회원이 선점한 링크는 존재하지 않는 것과 같은 404 다. 캡슐 정보와 참여자·수신자 미리보기(닉네임·프로필사진)를 함께 보여준다.")
  CommonResponse<CapsuleInvitePreviewResponse> getInvite(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "초대 토큰", example = "1a2b3c4d5e6f") String inviteToken);

  @Operation(
      summary = "받은 초대 목록 조회",
      description = "로그인한 회원에게 고정된 초대 중, 아직 응답하지 않은(수락·거절·만료되지 않은) 것만 최신순으로 보여준다.")
  CommonResponse<CapsuleInviteReceivedListResponse> getReceivedInvites(
      @Parameter(hidden = true) UserPrincipal principal);

  @Operation(
      summary = "초대 수락",
      description =
          "로그인한 회원이 초대를 수락해 캡슐에 참여한다. 이 링크를 처음 여는 것이면(미리보기 없이 바로 호출) 그 회원에게 고정하면서 진행한다."
              + " 나갔거나 제외됐던 회원이면 이번 초대가 정한 역할로 재참여시키고, 이미 참여 중이면 409 ALREADY_CAPSULE_MEMBER 다. 캡슐이"
              + " 잠긴 이후에는 409 CAPSULE_NOT_EDITABLE 이다.")
  CommonResponse<?> acceptInvite(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "초대 토큰", example = "1a2b3c4d5e6f") String inviteToken);

  @Operation(
      summary = "초대 거절",
      description = "로그인한 회원이 초대를 거절한다. 이미 다른 회원이 선점한 링크는 404 다. 이미 처리된 초대를 다시 거절해도 에러 없이 성공한다.")
  CommonResponse<?> rejectInvite(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "초대 토큰", example = "1a2b3c4d5e6f") String inviteToken);
}

package com.momento.server.domain.timecapsule.controller;

import com.momento.server.domain.timecapsule.dto.request.CapsuleInviteCreateRequest;
import com.momento.server.domain.timecapsule.dto.response.CapsuleInviteResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "CapsuleInvite", description = "캡슐 초대 링크 관리 API (OWNER 전용)")
public interface CapsuleInviteApi {

  @Operation(
      summary = "초대 링크 발급",
      description =
          "OWNER 만 호출할 수 있다. 해당 역할의 활성 링크가 있으면 그 링크를 그대로 돌려주고(캡슐 상세를 다시 열어도 같은 링크가 보이도록),"
              + " 없으면 새로 만든다. WRITING 상태의 캡슐에서만 가능하고, 캡슐 유형에 따라 초대 가능한 역할이 다르다(SELF 는 불가,"
              + " FRIEND 는 PARTICIPANT 만, GROUP 은 둘 다).")
  CommonResponse<CapsuleInviteResponse> issueInvite(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "타임캡슐 ID", example = "12") Long capsuleId,
      @Valid CapsuleInviteCreateRequest request);

  @Operation(
      summary = "초대 링크 재생성",
      description =
          "OWNER 만 호출할 수 있다. 해당 역할의 기존 활성 링크는 REVOKED 되고 새 링크가 발급된다. 거절은 그 초대(토큰)에 묶이므로,"
              + " 거절했던 회원도 재생성된 새 링크로는 다시 참여할 수 있다. WRITING 상태의 캡슐에서만 가능하다.")
  CommonResponse<CapsuleInviteResponse> regenerateInvite(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "타임캡슐 ID", example = "12") Long capsuleId,
      @Valid CapsuleInviteCreateRequest request);

  @Operation(
      summary = "초대 링크 취소",
      description = "OWNER 만 호출할 수 있다. 이미 취소되었거나 만료된 링크를 다시 취소해도 에러 없이 성공한다.")
  CommonResponse<?> cancelInvite(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "타임캡슐 ID", example = "12") Long capsuleId,
      @Parameter(description = "초대 ID", example = "31") Long inviteId);
}

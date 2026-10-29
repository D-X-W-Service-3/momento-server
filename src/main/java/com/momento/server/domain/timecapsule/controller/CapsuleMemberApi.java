package com.momento.server.domain.timecapsule.controller;

import com.momento.server.domain.timecapsule.dto.response.CapsuleMemberListResponse;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "CapsuleMember", description = "타임캡슐 멤버 API")
public interface CapsuleMemberApi {

  @Operation(
      summary = "캡슐 멤버 목록 조회",
      description =
          "참여자 관리 · 수신자 관리 화면에서 역할별 멤버 전체를 조회한다. role=PARTICIPANT 는 방장을 포함하고, 방장이 맨 앞이고 나머지는 참여한 순서다. "
              + "참여 중인 멤버라면 역할과 관계없이 볼 수 있다. 내보내기 버튼 노출은 canManage(방장이고 WRITING)와 isOwner(방장은 내보낼 수 없음)로 판단한다. "
              + "role 이 없거나 OWNER 이거나 없는 값이면 400, 멤버가 아니거나 없는 캡슐은 404 CAPSULE_NOT_FOUND 다.")
  CommonResponse<CapsuleMemberListResponse> getMembers(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "타임캡슐 ID", example = "21") Long capsuleId,
      @Parameter(
              description = "PARTICIPANT(방장 포함) 또는 RECIPIENT",
              example = "PARTICIPANT",
              required = true)
          MemberRole role);
}

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

  @Operation(
      summary = "캡슐 멤버 내보내기",
      description =
          "방장이 참여자 · 수신자를 내보낸다. 내보낸 사람은 이 캡슐의 상세 · 목록 · 편지 작성에서 404 를 받고, 그 사람이 이 캡슐에 쓴 편지(임시 저장 포함)는 지워진다. "
              + "다른 사람의 편지는 그대로다. 내보낸 사람도 새 초대 링크로 다시 들어올 수 있다. 응답 데이터는 없다. "
              + "멤버가 아니거나 없는 캡슐은 404 CAPSULE_NOT_FOUND, 방장이 아니면 403 CAPSULE_OWNER_ONLY, 편지 수정 마감 뒤면 409 CAPSULE_NOT_EDITABLE, "
              + "자기 자신(방장)이면 400 CANNOT_REMOVE_OWNER, 이 캡슐의 참여 중인 멤버가 아니면 404 CAPSULE_MEMBER_NOT_FOUND 다. "
              + "방장이 아니면 대상이 있는지와 관계없이 403 이다.")
  CommonResponse<?> removeMember(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "타임캡슐 ID", example = "21") Long capsuleId,
      @Parameter(description = "내보낼 캡슐 멤버 ID. 멤버 목록의 memberId 다.", example = "102") Long memberId);
}

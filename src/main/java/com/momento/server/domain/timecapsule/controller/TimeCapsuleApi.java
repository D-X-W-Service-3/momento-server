package com.momento.server.domain.timecapsule.controller;

import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleDetailResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "TimeCapsule", description = "타임캡슐 API")
public interface TimeCapsuleApi {

  @Operation(
      summary = "타임캡슐 생성",
      description =
          "로그인한 사용자가 캡슐을 만든다. 생성자는 OWNER 로 참여자에 함께 등록되고, 캡슐은 편지를 쓸 수 있는 WRITING 상태로 시작한다. 편지 작성 마감 일시는 비워 두며, 이 경우 공개 직전까지 편지를 쓸 수 있다.")
  CommonResponse<TimeCapsuleResponse> createTimeCapsule(
      @Parameter(hidden = true) UserPrincipal principal, @Valid TimeCapsuleCreateRequest request);

  @Operation(
      summary = "타임캡슐 상세 조회",
      description =
          "참여 중인 캡슐의 상세와 내 역할, 볼 수 있는 범위를 조회한다. 없는 캡슐, 삭제된 캡슐, 참여하지 않았거나 나간 캡슐은 구분하지 않고 모두 404 로 응답한다.")
  CommonResponse<TimeCapsuleDetailResponse> getTimeCapsule(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "타임캡슐 ID", example = "12") Long capsuleId);
}

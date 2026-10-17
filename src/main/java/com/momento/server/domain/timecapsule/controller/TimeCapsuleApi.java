package com.momento.server.domain.timecapsule.controller;

import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleCreateResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleDetailResponse;
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
          "로그인한 사용자가 캡슐을 만들고 캡슐 ID 를 돌려준다. 생성자는 OWNER 로 참여자에 함께 등록되고, 캡슐은 편지를 쓸 수 있는 WRITING 상태로 시작한다. "
              + "편지 수정 마감일은 그날 23:59:59 에 잠기고 개봉일은 그날 00:00 에 열린다. 마감일이 오늘보다 이르거나 개봉일 2일 전보다 늦으면 400 INVALID_CAPSULE_SCHEDULE 이다. "
              + "나에게 쓰는 캡슐(SELF)은 참여자가 생성자 한 명뿐이라 공개 범위를 ALL_MEMBERS 로만 받는다. 다른 값을 보내면 400 이다.")
  CommonResponse<TimeCapsuleCreateResponse> createTimeCapsule(
      @Parameter(hidden = true) UserPrincipal principal, @Valid TimeCapsuleCreateRequest request);

  @Operation(
      summary = "타임캡슐 상세 조회",
      description =
          "참여 중인 캡슐의 상세와 내 역할, 볼 수 있는 범위를 조회한다. 없는 캡슐, 삭제된 캡슐, 참여하지 않았거나 나간 캡슐은 구분하지 않고 모두 404 로 응답한다.")
  CommonResponse<TimeCapsuleDetailResponse> getTimeCapsule(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "타임캡슐 ID", example = "12") Long capsuleId);
}

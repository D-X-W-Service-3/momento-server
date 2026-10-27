package com.momento.server.domain.user.controller;

import com.momento.server.domain.user.dto.response.MyProfileResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User", description = "회원 API")
public interface UserApi {

  @Operation(
      summary = "내 정보 조회",
      description =
          "마이페이지와 프로필 정보 수정 화면에서 로그인한 회원의 프로필과 활동 요약을 조회한다. "
              + "활동 요약은 조회할 때 세며, 캡슐 기준은 타임캡슐 목록 조회의 탭별 개수와 같다(내가 속한, 삭제되지 않은 캡슐). "
              + "beforeOpenCapsuleCount 는 WRITING 과 LOCKED 를 합친 수다.")
  CommonResponse<MyProfileResponse> getMyProfile(@Parameter(hidden = true) UserPrincipal principal);
}

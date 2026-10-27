package com.momento.server.domain.user.controller;

import com.momento.server.domain.user.dto.request.NicknameAvailabilityRequest;
import com.momento.server.domain.user.dto.request.UserUpdateRequest;
import com.momento.server.domain.user.dto.response.MyProfileResponse;
import com.momento.server.domain.user.dto.response.NicknameAvailabilityResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "User", description = "회원 API")
public interface UserApi {

  @Operation(
      summary = "내 정보 조회",
      description =
          "마이페이지와 프로필 정보 수정 화면에서 로그인한 회원의 프로필과 활동 요약을 조회한다. "
              + "활동 요약은 조회할 때 세며, 캡슐 기준은 타임캡슐 목록 조회의 탭별 개수와 같다(내가 속한, 삭제되지 않은 캡슐). "
              + "beforeOpenCapsuleCount 는 WRITING 과 LOCKED 를 합친 수다.")
  CommonResponse<MyProfileResponse> getMyProfile(@Parameter(hidden = true) UserPrincipal principal);

  @Operation(
      summary = "내 정보 수정",
      description =
          "첫 로그인 후 프로필 설정과 마이페이지의 프로필 정보 수정에서 쓴다. 보낸 필드만 바꾸고, 보내지 않았거나 null 인 필드는 그대로 둔다. "
              + "문자열은 앞뒤 공백을 뺀다. 이메일 · 전화번호는 빈 문자열을 보내면 지운다. 생일은 바꿀 수만 있고 지울 수 없다. "
              + "닉네임은 2~20자이고 다른 회원이 쓰고 있으면 409 NICKNAME_DUPLICATED 다. 지금 내 닉네임을 그대로 보내면 성공이다. "
              + "중복은 DB 의 닉네임 UNIQUE 로 판정하므로, 운영 DB 의 정렬 기준에 따라 대소문자만 다른 닉네임(Sangrae · sangrae)은 같은 닉네임으로 볼 수 있다. "
              + "형식이 맞지 않으면 400 INVALID_INPUT_VALUE 다. 수정 후 내 정보 조회와 같은 형태를 돌려준다.")
  CommonResponse<MyProfileResponse> updateMyProfile(
      @Parameter(hidden = true) UserPrincipal principal, @Valid UserUpdateRequest request);

  @Operation(
      summary = "닉네임 사용 가능 여부 확인",
      description =
          "프로필 설정에서 닉네임을 입력하면 호출한다. 다른 회원(탈퇴한 회원 포함)이 쓰지 않으면 available 이 true 다. "
              + "지금 내 닉네임이면 true 다. 앞뒤 공백을 빼고 확인하며, 2~20자가 아니면 400 INVALID_INPUT_VALUE 다. "
              + "내 정보 수정과 같은 기준이라, 운영 DB 의 정렬 기준에 따라 대소문자만 다른 닉네임은 이미 쓰는 닉네임으로 볼 수 있다. "
              + "확인 뒤 저장 사이에 다른 회원이 가져갈 수 있으므로, 최종 판정은 내 정보 수정의 409 다.")
  CommonResponse<NicknameAvailabilityResponse> checkNickname(
      @Parameter(hidden = true) UserPrincipal principal,
      @ParameterObject @Valid NicknameAvailabilityRequest request);
}

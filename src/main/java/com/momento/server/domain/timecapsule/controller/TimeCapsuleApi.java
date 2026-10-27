package com.momento.server.domain.timecapsule.controller;

import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleCreateResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleDetailResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleListResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleOpenResponse;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
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
      summary = "타임캡슐 목록 조회",
      description =
          "내가 속한(방장 · 참여자 · 수신자) 캡슐을 개봉일 오름차순으로 조회한다. 삭제된 캡슐과 내보내진 캡슐은 나오지 않는다. "
              + "counts 는 탭에 표시할 상태별 개수로, keyword 는 적용하고 status 는 적용하지 않는다. "
              + "다음 페이지는 응답의 nextCursor 를 cursor 로 넘겨 받는다. 커서는 해석하지 말고 그대로 넘긴다. "
              + "dDay · deadlineDDay 는 서버의 한국 시간 날짜 기준이다. "
              + "size 가 1~100 밖이거나, status 가 없는 값이거나, 커서가 깨졌으면 400 이다.")
  CommonResponse<TimeCapsuleListResponse> getTimeCapsules(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "상태 탭. 없으면 전체", example = "WRITING") CapsuleStatus status,
      @Parameter(description = "검색어. 캡슐 이름 · 설명 · 멤버 닉네임에서 찾는다", example = "제주") String keyword,
      @Parameter(description = "다음 페이지 커서. 첫 조회 때는 보내지 않는다") String cursor,
      @Parameter(description = "한 번에 조회할 개수. 1~100", example = "20") int size);

  @Operation(
      summary = "타임캡슐 상세 조회",
      description =
          "참여 중인 캡슐의 상세와 내 역할, 볼 수 있는 범위를 조회한다. 없는 캡슐, 삭제된 캡슐, 참여하지 않았거나 나간 캡슐은 구분하지 않고 모두 404 로 응답한다.")
  CommonResponse<TimeCapsuleDetailResponse> getTimeCapsule(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "타임캡슐 ID", example = "12") Long capsuleId);

  @Operation(
      summary = "타임캡슐 개봉 확인",
      description =
          "열린 캡슐의 개봉 화면에서 '타임캡슐 확인하기' 를 누르면 호출한다. 로그인한 멤버가 이 캡슐을 확인했다고 기록하고, 처음 확인한 일시를 돌려준다. "
              + "이미 확인한 캡슐에 다시 호출해도 200 이고 openedAt 은 처음 값 그대로다. 역할과 관계없이 멤버면 확인할 수 있다. "
              + "아직 공개되지 않은 캡슐은 409 CAPSULE_NOT_OPENED, 멤버가 아니거나 없는 캡슐은 404 CAPSULE_NOT_FOUND 다.")
  CommonResponse<TimeCapsuleOpenResponse> openTimeCapsule(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "타임캡슐 ID", example = "21") Long capsuleId);
}

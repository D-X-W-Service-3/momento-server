package com.momento.server.domain.anniversary.controller;

import com.momento.server.domain.anniversary.dto.request.AnniversaryRequest;
import com.momento.server.domain.anniversary.dto.response.AnniversaryCreateResponse;
import com.momento.server.domain.anniversary.dto.response.AnniversaryDetailResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Anniversary", description = "기념일 API")
public interface AnniversaryApi {

  @Operation(
      summary = "기념일 등록",
      description =
          "기념일 생성 화면에서 기념일과 알림 설정을 등록한다. 문자열은 앞뒤 공백을 빼고, 공백뿐인 메모는 저장하지 않는다. "
              + "알림 시각을 보내지 않으면 09:00 이고, 초는 버린다. 알림 받기를 끄면 미리 알림은 저장하지 않고 알림 시각은 보관한다. "
              + "미리 알림은 1 · 3 · 5 · 7 · 10 일 전만 받는다. 형식이 맞지 않으면 400 INVALID_INPUT_VALUE 다. "
              + "등록된 기념일 ID 를 돌려준다.")
  CommonResponse<AnniversaryCreateResponse> createAnniversary(
      @Parameter(hidden = true) UserPrincipal principal, @Valid AnniversaryRequest request);

  @Operation(
      summary = "기념일 상세 조회",
      description =
          "기념일 수정 화면에 채울 값을 조회한다. 내가 등록한 기념일만 조회할 수 있고, 없는 기념일과 다른 회원의 기념일은 똑같이 404 "
              + "ANNIVERSARY_NOT_FOUND 다. nextDate 는 다가오는 기념일 날짜다. 매년 반복이면 오늘 이후(오늘 포함) 가장 가까운 같은 월 · 일이고, "
              + "등록한 날짜가 아직 오지 않았으면 그 날짜다. 2월 29일은 평년에 2월 28일로 본다. 반복하지 않으면 등록한 날짜 그대로다. "
              + "dDay 는 오늘부터 nextDate 까지 남은 일수로, 당일 0, 지난 기념일은 음수다.")
  CommonResponse<AnniversaryDetailResponse> getAnniversary(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "기념일 ID", example = "7") Long anniversaryId);

  @Operation(
      summary = "기념일 수정",
      description =
          "기념일 수정 화면의 '수정하기' 로 저장한다. 등록과 같은 Body 를 받아 모든 값을 통째로 바꾼다. 보내지 않았거나 null 인 선택값은 "
              + "\"없음\" 이라, 메모 · 미리 알림을 null 로 보내면 지워지고 알림 시각을 보내지 않으면 09:00 이 된다. 검증 규칙은 등록과 같다. "
              + "내가 등록한 기념일만 고칠 수 있고, 없는 기념일과 다른 회원의 기념일은 똑같이 404 ANNIVERSARY_NOT_FOUND 다. "
              + "수정 후 상세 조회와 같은 형태를 돌려준다.")
  CommonResponse<AnniversaryDetailResponse> updateAnniversary(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "기념일 ID", example = "7") Long anniversaryId,
      @Valid AnniversaryRequest request);

  @Operation(
      summary = "기념일 삭제",
      description =
          "기념일을 지운다. 되살릴 수 없다. 내가 등록한 기념일만 지울 수 있고, 없는 기념일과 다른 회원의 기념일은 똑같이 404 "
              + "ANNIVERSARY_NOT_FOUND 다. 응답 데이터는 없다.")
  CommonResponse<?> deleteAnniversary(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "기념일 ID", example = "7") Long anniversaryId);
}

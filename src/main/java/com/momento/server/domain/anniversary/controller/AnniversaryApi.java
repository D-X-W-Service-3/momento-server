package com.momento.server.domain.anniversary.controller;

import com.momento.server.domain.anniversary.dto.request.AnniversaryRequest;
import com.momento.server.domain.anniversary.dto.response.AnniversaryCreateResponse;
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
}

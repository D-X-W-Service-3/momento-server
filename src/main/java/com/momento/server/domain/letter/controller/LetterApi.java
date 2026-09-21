package com.momento.server.domain.letter.controller;

import com.momento.server.domain.letter.dto.request.LetterCreateRequest;
import com.momento.server.domain.letter.dto.response.LetterResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Letter", description = "편지 API")
public interface LetterApi {
  @Operation(
      summary = "편지 초안 생성",
      description =
          "ACTIVE 참여자만 작성할 수 있으며 수신자는 ALL_MEMBERS 공개 범위에서만 작성할 수 있다. "
              + "WRITING 상태에서 공개일 및 편지 마감 전까지 DRAFT를 생성한다. "
              + "삭제되지 않은 내 편지가 있으면 409 LETTER_ALREADY_EXISTS다. "
              + "중복이 없을 때 수신자의 작성 권한이 없으면 403 LETTER_WRITING_NOT_ALLOWED, "
              + "작성 상태나 시간 조건을 충족하지 못하면 409 LETTER_WRITING_CLOSED다. 생성 성공은 201이다.")
  CommonResponse<LetterResponse> create(
      @Parameter(hidden = true) UserPrincipal principal,
      Long capsuleId,
      @Valid LetterCreateRequest request);

  @Operation(
      summary = "내 편지 조회",
      description =
          "ACTIVE 참여자는 공개 전후와 무관하게 본인의 초안/제출 편지를 조회한다. 미작성 또는 삭제된 편지는 404 LETTER_NOT_FOUND, 접근 불가 캡슐은 404 CAPSULE_NOT_FOUND다.")
  CommonResponse<LetterResponse> getMine(
      @Parameter(hidden = true) UserPrincipal principal, Long capsuleId);
}

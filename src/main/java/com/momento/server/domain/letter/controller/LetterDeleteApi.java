package com.momento.server.domain.letter.controller;

import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Letter", description = "편지 API")
public interface LetterDeleteApi {
  @Operation(
      summary = "내 편지 삭제",
      description =
          "본인의 삭제되지 않은 편지를 소프트 삭제한다. "
              + "ACTIVE 참여자이며 WRITING 상태에서 작성 마감·공개 시각 전이어야 한다. "
              + "성공은 data 없는 200이다. 없는·타인·이미 삭제된 편지는 404 LETTER_NOT_FOUND, "
              + "삭제된·접근 불가 캡슐은 404 CAPSULE_NOT_FOUND, 기간 종료는 409 LETTER_WRITING_CLOSED다.")
  CommonResponse<Void> delete(@Parameter(hidden = true) UserPrincipal principal, Long letterId);
}

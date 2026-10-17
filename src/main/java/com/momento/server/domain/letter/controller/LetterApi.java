package com.momento.server.domain.letter.controller;

import com.momento.server.domain.letter.dto.request.LetterCreateRequest;
import com.momento.server.domain.letter.dto.request.LetterUpdateRequest;
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

  @Operation(
      summary = "내 편지 제출",
      description =
          "저장된 본문을 제출한다. 최초 제출은 생성과 같은 역할·기간 조건을 적용하며 빈 본문·공백 본문은 400 LETTER_CONTENT_REQUIRED다. "
              + "재제출은 ACTIVE 참여와 본인 편지 존재를 확인한 뒤 마감·공개 후에도 성공하며 최초 제출 시각을 유지한다. "
              + "접근 불가 캡슐은 404 CAPSULE_NOT_FOUND, 편지 없음은 404 LETTER_NOT_FOUND, 역할 거절은 403 LETTER_WRITING_NOT_ALLOWED, 마감은 409 LETTER_WRITING_CLOSED다.")
  CommonResponse<LetterResponse> submit(
      @Parameter(hidden = true) UserPrincipal principal, Long capsuleId);

  @Operation(
      summary = "내 편지 삭제",
      description =
          "DRAFT/SUBMITTED를 소프트 삭제한다. 생성과 같은 역할·기간 조건을 적용하며 마감 정각부터 409 LETTER_WRITING_CLOSED다. "
              + "접근 불가 캡슐은 404 CAPSULE_NOT_FOUND, 편지 없음·반복 삭제는 404 LETTER_NOT_FOUND, 역할 거절은 403 LETTER_WRITING_NOT_ALLOWED다. "
              + "성공은 200이며 작성 조건을 만족하면 새 DRAFT를 생성할 수 있다.")
  CommonResponse<?> delete(@Parameter(hidden = true) UserPrincipal principal, Long capsuleId);

  @Operation(
      summary = "내 편지 수정",
      description =
          "DRAFT/SUBMITTED의 본문·테마 중 보낸 필드만 수정한다. 생략은 유지, content null은 400 INVALID_INPUT_VALUE, themeType null은 테마 해제다. "
              + "상태·최초 제출 시각을 유지하며 제출된 편지의 빈 본문·공백 본문은 400 LETTER_CONTENT_REQUIRED다. "
              + "본문 최대 10000자, 테마 최대 20자다. 빈 객체도 접근·작성 조건을 검사한 뒤 변경 없이 반환한다. "
              + "접근 불가 캡슐은 404 CAPSULE_NOT_FOUND, 편지 없음은 404 LETTER_NOT_FOUND, 역할 거절은 403 LETTER_WRITING_NOT_ALLOWED, 마감 정각부터 409 LETTER_WRITING_CLOSED다.")
  CommonResponse<LetterResponse> update(
      @Parameter(hidden = true) UserPrincipal principal,
      Long capsuleId,
      @Valid LetterUpdateRequest request);
}

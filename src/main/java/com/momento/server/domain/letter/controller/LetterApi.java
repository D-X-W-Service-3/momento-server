package com.momento.server.domain.letter.controller;

import com.momento.server.domain.letter.dto.request.LetterCreateRequest;
import com.momento.server.domain.letter.dto.response.LetterResponse;
import com.momento.server.domain.letter.dto.response.PublicLetterListResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Letter", description = "편지 API")
public interface LetterApi {
  @Operation(
      summary = "편지 작성",
      description =
          "ACTIVE 참여자만 작성할 수 있으며 수신자는 ALL_MEMBERS에서만 작성할 수 있다. "
              + "WRITING 상태에서 공개일 및 편지 마감 전까지 저장과 동시에 제출한다. "
              + "삭제되지 않은 내 편지가 있으면 409 LETTER_ALREADY_EXISTS다. "
              + "수신자 작성 권한이 없으면 403 LETTER_WRITING_NOT_ALLOWED, 작성 상태나 시간 조건을 충족하지 못하면 409 LETTER_WRITING_CLOSED다. 생성 성공은 data 없는 201 응답이다. AI 이미지 생성은 포함하지 않는다.")
  CommonResponse<Void> create(
      @Parameter(hidden = true) UserPrincipal principal,
      Long capsuleId,
      @Valid LetterCreateRequest request);

  @Operation(
      summary = "내 편지 조회",
      description =
          "ACTIVE 참여자는 공개 전후와 무관하게 본인 편지 한 통을 조회한다. 제목·화풍·내용·작성 일시와 삭제 가능 여부를 반환하며 기존 편지의 제목·화풍은 null일 수 있다. canDelete는 WRITING이며 작성 마감·공개 시각 전일 때만 true다. 미작성 또는 삭제된 편지는 404 LETTER_NOT_FOUND, 접근 불가 캡슐은 404 CAPSULE_NOT_FOUND다.")
  CommonResponse<LetterResponse> getMine(
      @Parameter(hidden = true) UserPrincipal principal, Long capsuleId);

  @Operation(
      summary = "공개된 편지 목록 조회",
      description =
          "ACTIVE 참여자만 조회한다. OPENED 이전은 409 CAPSULE_NOT_OPENED, 접근 불가 캡슐은 404 CAPSULE_NOT_FOUND다. RECIPIENT_AND_AUTHOR에서는 수신자가 모든 편지를, 나머지는 자신의 편지만 본다. ALL_MEMBERS에서는 참여자가 전체 편지를 본다. 삭제되지 않은 제출 편지를 작성 시각·ID 오름차순으로 반환하며 빈 결과는 빈 배열이다. 기존 제목·화풍은 null일 수 있으며 AI 연계 보류로 imageUrl은 null이다.")
  CommonResponse<PublicLetterListResponse> getLetters(
      @Parameter(hidden = true) UserPrincipal principal, Long capsuleId);
}

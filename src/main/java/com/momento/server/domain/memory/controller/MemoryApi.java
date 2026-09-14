package com.momento.server.domain.memory.controller;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.response.MemoryListResponse;
import com.momento.server.domain.memory.dto.response.MemoryResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Memory", description = "추억 아카이빙 API")
public interface MemoryApi {

  @Operation(
      summary = "추억 등록",
      description =
          "제목·내용·추억 날짜와, 클라이언트가 이미 업로드해 확보한 이미지 URL 목록(최대 10장)을 받아 저장한다. "
              + "이미지 파일 업로드 자체는 이 API 의 범위가 아니다. 공개 범위는 요청으로 받지 않고 항상 PRIVATE 로 저장된다.")
  CommonResponse<MemoryResponse> create(
      UserPrincipal principal, @Valid MemoryCreateRequest request);

  @Operation(
      summary = "추억 목록 조회",
      description =
          "로그인한 사용자가 직접 등록한 추억만 memoryDate 최신순으로 조회한다. "
              + "타임캡슐에서 옮겨진 추억과 삭제된 추억은 제외한다. 각 항목에는 대표 이미지 1장만 담긴다.")
  CommonResponse<MemoryListResponse> findMyMemories(
      UserPrincipal principal,
      @Parameter(description = "제목 부분검색어", example = "제주") String title,
      @Parameter(description = "연도 필터", example = "2026") Integer year,
      @Parameter(description = "페이지 번호(0부터)", example = "0") int page,
      @Parameter(description = "페이지당 개수(1~100)", example = "20") int size);
}

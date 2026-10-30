package com.momento.server.domain.memory.controller;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.response.MemoryListResponse;
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
          "아카이브 추가 화면에서 이름 · 내용 · 추억 날짜 · 사진을 받아 저장한다. 사진은 먼저 이미지 업로드 URL 발급(target=MEMORY)으로 S3 에"
              + " 올리고, 받은 imageKey 를 보여줄 순서대로 imageKeys 에 담는다(최대 8장). representativeImageKey 를 주면 그 사진을"
              + " 첫 번째로 옮겨 대표 사진으로 쓰고, 없으면 첫 사진이 대표 사진이다. 내 key 가 아니거나 같은 key 가 두 번 있거나 대표 사진 key"
              + " 가 목록에 없으면 400 INVALID_IMAGE_KEY 다. 이름은 15자, 내용은 45자까지다."
              + " 공개 범위는 요청으로 받지 않고 항상 PRIVATE 로 저장된다. 등록 후 목록을 다시 조회하므로 응답 데이터는 없다.")
  CommonResponse<?> create(
      @Parameter(hidden = true) UserPrincipal principal, @Valid MemoryCreateRequest request);

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

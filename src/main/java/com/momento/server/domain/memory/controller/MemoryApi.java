package com.momento.server.domain.memory.controller;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.response.MemoryDetailResponse;
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
      summary = "아카이브 상세 조회",
      description =
          "아카이브 상세 화면(사진 슬라이드 · 날짜 · 이름 · 내용 · 사진 목록)을 조회한다. 본인이 등록한 아카이브만 조회할 수 있다. 사진은 displayOrder"
              + " 오름차순이고 첫 사진이 대표 사진(isRepresentative)이다. imageUrl 은 조회 시점에 만든 1시간짜리 presigned GET URL,"
              + " imageKey 는 수정 요청에 그대로 보내는 값이다. 없는 아카이브 · 삭제된 아카이브 · 다른 회원의 아카이브는 똑같이 404"
              + " MEMORY_NOT_FOUND 다.")
  CommonResponse<MemoryDetailResponse> getMemory(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "아카이브 식별자", example = "3") Long memoryId);

  @Operation(
      summary = "추억 목록 조회",
      description =
          "로그인한 사용자가 직접 등록한 추억만 memoryDate 최신순으로 조회한다. "
              + "타임캡슐에서 옮겨진 추억과 삭제된 추억은 제외한다. 각 항목에는 대표 이미지 1장만 담긴다.")
  CommonResponse<MemoryListResponse> findMyMemories(
      UserPrincipal principal,
      @Parameter(description = "검색어. 아카이브 이름 · 내용에서 찾는다", example = "카페") String keyword,
      @Parameter(description = "연도 필터", example = "2026") Integer year,
      @Parameter(description = "페이지 번호(0부터)", example = "0") int page,
      @Parameter(description = "페이지당 개수(1~100)", example = "20") int size);
}

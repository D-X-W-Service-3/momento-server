package com.momento.server.domain.memory.controller;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.request.MemoryUpdateRequest;
import com.momento.server.domain.memory.dto.response.MemoryDetailResponse;
import com.momento.server.domain.memory.dto.response.MemoryListResponse;
import com.momento.server.domain.memory.dto.response.MemoryShareResponse;
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
          "아카이브 상세 화면(사진 슬라이드 · 날짜 · 이름 · 내용 · 사진 목록)을 조회한다. 본인이 등록한 아카이브와 공유받아 저장한 아카이브를 조회할 수 있고, 저장한 아카이브는 authorNickname 이 공유한 사람 · isMine 이 false 다. 사진은 displayOrder"
              + " 오름차순이고 첫 사진이 대표 사진(isRepresentative)이다. imageUrl 은 조회 시점에 만든 1시간짜리 presigned GET URL,"
              + " imageKey 는 수정 요청에 그대로 보내는 값이다. 없는 아카이브 · 삭제된 아카이브 · 다른 회원의 아카이브는 똑같이 404"
              + " MEMORY_NOT_FOUND 다.")
  CommonResponse<MemoryDetailResponse> getMemory(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "아카이브 식별자", example = "3") Long memoryId);

  @Operation(
      summary = "아카이브 수정",
      description =
          "아카이브 수정 화면에서 사진 · 이름 · 내용을 수정한다. 등록과 같은 필드를 받지만 모두 선택이고, title · content · memoryDate 는 보낸"
              + " 경우에만 바꾼다. imageKeys 를 보내면 사진 목록 전체를 그 값으로 바꾸고 빠진 사진은 삭제된다. 기존 사진은 상세 조회의 imageKey 를,"
              + " 새 사진은 업로드 후 받은 key 를 넣는다. representativeImageKey 를 주면 그 사진을 첫 번째(대표 사진)로 옮긴다. 내 key 가"
              + " 아니거나 중복이거나 대표 사진 key 가 목록에 없으면 400 INVALID_IMAGE_KEY 다. 없는 아카이브 · 삭제된 아카이브 · 다른 회원의"
              + " 아카이브는 똑같이 404 MEMORY_NOT_FOUND 다. 응답 데이터는 없다.")
  CommonResponse<?> updateMemory(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "아카이브 식별자", example = "3") Long memoryId,
      @Valid MemoryUpdateRequest request);

  @Operation(
      summary = "아카이브 삭제",
      description =
          "아카이브 상세 메뉴의 '삭제하기' 로 아카이브를 삭제한다. memories.deleted_at 을 채우는 소프트 삭제이고, 지운 아카이브는 상세 · 목록 ·"
              + " 캘린더에 나오지 않는다. 본인이 등록한 아카이브만 삭제할 수 있고, 없는 아카이브 · 이미 삭제된 아카이브 · 다른 회원의 아카이브는"
              + " 똑같이 404 MEMORY_NOT_FOUND 다. 응답 데이터는 없다.")
  CommonResponse<?> deleteMemory(
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

  @Operation(
      summary = "아카이브 공유 링크 발급",
      description =
          "내 아카이브를 링크로 공유하려고 공유 토큰을 발급한다. 처음이면 만들고, 이미 공유했으면 같은 토큰을 준다. 링크 하나를 여러 사람이 받을 수 있고"
              + " 만료는 없다. 공유 링크의 형식은 프론트가 정하고, 받는 사람은 링크로 받기에 이 토큰을 보낸다. 본인이 등록한 아카이브만 공유할 수 있고,"
              + " 없는 아카이브 · 삭제된 아카이브 · 다른 회원의 아카이브는 똑같이 404 MEMORY_NOT_FOUND 다.")
  CommonResponse<MemoryShareResponse> shareMemory(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "아카이브 식별자", example = "3") Long memoryId);

  @Operation(
      summary = "공유 링크로 받기",
      description =
          "공유 링크를 열면 그 아카이브를 받은 아카이브에 넣는다. 아카이브 탭의 \"공유받은 아카이브가 있어요\" 에서 확인해 저장하거나 닫는다. 같은 링크를"
              + " 여러 번 열어도 한 건이고, 닫았던 아카이브면 다시 받은 아카이브에 나온다. 이미 저장한 아카이브면 그대로 성공한다. 내 아카이브의 링크면"
              + " 400 CANNOT_RECEIVE_OWN_MEMORY, 없는 토큰 · 삭제된 아카이브는 404 MEMORY_NOT_FOUND 다. 응답 데이터는 없다.")
  CommonResponse<?> receiveSharedMemory(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "공유 토큰", example = "3f0a5c9e2b1d4f7a8c3e9d2b7a6e1f00")
          String shareToken);
}

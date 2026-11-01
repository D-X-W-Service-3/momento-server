package com.momento.server.domain.memory.controller;

import com.momento.server.domain.memory.dto.response.ReceivedMemoryListResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Received Memory", description = "공유받은 아카이브 API")
public interface ReceivedMemoryApi {

  @Operation(
      summary = "받은 아카이브 조회",
      description =
          "아카이브 탭의 \"공유받은 아카이브가 있어요 · 지금 확인하기\" 에서 공유 링크로 받고 아직 저장 · 닫기를 하지 않은 아카이브를 받은 순으로"
              + " 조회한다. 공유한 사람이 원본을 삭제한 아카이브는 나오지 않는다. 사진은 displayOrder 오름차순이고 첫 사진이 대표 사진이며,"
              + " imageUrl 은 1시간짜리 presigned GET URL 이다. 받은 사람은 수정할 수 없어 imageKey 는 주지 않는다.")
  CommonResponse<ReceivedMemoryListResponse> getReceivedMemories(
      @Parameter(hidden = true) UserPrincipal principal);

  @Operation(
      summary = "받은 아카이브 저장",
      description =
          "받은 아카이브를 내 아카이브에 저장한다. 복사본을 만들지 않고 원본을 가리키므로 공유한 사람이 수정하면 같이 바뀌고 삭제하면 같이 사라지며,"
              + " 저장한 사람은 수정할 수 없다. 저장하면 받은 아카이브 조회에서 빠진다. 없는 · 다른 회원의 · 이미 저장하거나 닫은 · 원본이 삭제된"
              + " 받은 아카이브는 똑같이 404 RECEIVED_MEMORY_NOT_FOUND 다.")
  CommonResponse<?> saveReceivedMemory(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "받은 아카이브 식별자", example = "4") Long receivedMemoryId);

  @Operation(
      summary = "받은 아카이브 닫기",
      description =
          "받은 아카이브를 닫는다. 받은 아카이브 조회에서 빠지고 다시 나오지 않는다. 같은 공유 링크를 다시 열면 다시 받은 아카이브에 나온다. 대상이 없는"
              + " 경우는 저장과 같이 404 RECEIVED_MEMORY_NOT_FOUND 다. 응답 데이터는 없다.")
  CommonResponse<?> dismissReceivedMemory(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "받은 아카이브 식별자", example = "4") Long receivedMemoryId);
}

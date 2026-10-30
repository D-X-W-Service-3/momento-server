package com.momento.server.domain.memory.dto.response;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

@Schema(description = "추억 목록의 항목 하나")
public record MemorySummary(
    @Schema(description = "추억 ID", example = "7") Long memoryId,
    @Schema(description = "추억 제목", example = "제주도 여행") String title,
    @Schema(description = "추억이 있었던 날짜", example = "2026-08-15") LocalDate memoryDate,
    @Schema(description = "대표 사진 URL(presigned GET, 1시간). 사진이 없으면 null 이다.", nullable = true)
        String thumbnailUrl,
    @Schema(description = "사진 수", example = "4") int imageCount,
    @Schema(description = "작성자 닉네임", example = "유빈") String authorNickname) {

  /** images 는 display_order 오름차순이어야 한다. 첫 번째가 대표 사진이다. viewUrlOf 는 저장된 key 를 조회 주소로 바꾼다. */
  public static MemorySummary of(
      Memory memory,
      List<MemoryImage> images,
      String authorNickname,
      Function<String, String> viewUrlOf) {
    String thumbnailUrl = images.isEmpty() ? null : viewUrlOf.apply(images.get(0).getImageUrl());

    return new MemorySummary(
        memory.getId(),
        memory.getTitle(),
        memory.getMemoryDate(),
        thumbnailUrl,
        images.size(),
        authorNickname);
  }
}

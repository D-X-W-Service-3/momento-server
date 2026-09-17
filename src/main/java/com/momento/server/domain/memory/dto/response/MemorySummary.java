package com.momento.server.domain.memory.dto.response;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "추억 목록의 항목 하나")
public record MemorySummary(
    @Schema(description = "추억 ID", example = "7") Long memoryId,
    @Schema(description = "추억 제목", example = "제주도 여행") String title,
    @Schema(description = "추억이 있었던 날짜", example = "2026-08-15") LocalDate memoryDate,
    @Schema(description = "대표 이미지 URL. 첨부 이미지가 없으면 null 이다.") String thumbnailUrl,
    @Schema(description = "첨부된 이미지 개수", example = "2") int imageCount) {

  /** images 는 display_order 오름차순이어야 한다. 첫 번째가 대표 이미지가 된다. */
  public static MemorySummary of(Memory memory, List<MemoryImage> images) {
    String thumbnailUrl = images.isEmpty() ? null : images.get(0).getImageUrl();

    return new MemorySummary(
        memory.getId(), memory.getTitle(), memory.getMemoryDate(), thumbnailUrl, images.size());
  }
}

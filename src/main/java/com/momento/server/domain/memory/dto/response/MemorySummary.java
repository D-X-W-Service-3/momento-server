package com.momento.server.domain.memory.dto.response;

import com.momento.server.domain.memory.entity.Memory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "추억 목록의 항목 하나")
public record MemorySummary(
    @Schema(description = "추억 ID", example = "7") Long memoryId,
    @Schema(description = "추억 제목", example = "제주도 여행") String title,
    @Schema(description = "추억이 있었던 날짜", example = "2026-08-15") LocalDate memoryDate,
    @Schema(description = "대표 이미지 URL. 첨부 이미지가 없으면 null 이다.") String thumbnailUrl) {

  public static MemorySummary of(Memory memory, String thumbnailUrl) {
    return new MemorySummary(
        memory.getId(), memory.getTitle(), memory.getMemoryDate(), thumbnailUrl);
  }
}

package com.momento.server.domain.memory.dto.response;

import com.momento.server.domain.memory.entity.Memory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "추억 목록 조회 결과")
public record MemoryListResponse(
    @Schema(description = "추억 목록") List<MemorySummary> memories,
    @Schema(description = "현재 페이지 번호(0부터 시작)", example = "0") int page,
    @Schema(description = "페이지당 개수", example = "20") int size,
    @Schema(description = "조건에 맞는 전체 개수", example = "2") long totalElements,
    @Schema(description = "전체 페이지 수", example = "1") int totalPages,
    @Schema(description = "다음 페이지 존재 여부", example = "false") boolean hasNext) {

  public static MemoryListResponse of(Page<Memory> page, List<MemorySummary> memories) {
    return new MemoryListResponse(
        memories,
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.hasNext());
  }
}

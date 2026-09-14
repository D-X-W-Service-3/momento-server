package com.momento.server.domain.memory.dto.response;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryVisibilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "추억 등록 결과")
public record MemoryResponse(
    @Schema(description = "추억 ID", example = "7") Long memoryId,
    @Schema(description = "추억 제목", example = "제주도 여행") String title,
    @Schema(description = "추억 내용", example = "바다가 정말 예뻤다") String content,
    @Schema(description = "추억이 있었던 날짜", example = "2026-08-15") LocalDate memoryDate,
    @Schema(description = "공개 범위. 등록 시에는 항상 PRIVATE 이다.") MemoryVisibilityType visibilityType,
    @Schema(description = "첨부된 이미지 URL 목록(표시 순서대로)") List<String> imageUrls,
    @Schema(description = "등록 일시") LocalDateTime createdAt) {

  public static MemoryResponse of(Memory memory, List<String> imageUrls) {
    return new MemoryResponse(
        memory.getId(),
        memory.getTitle(),
        memory.getContent(),
        memory.getMemoryDate(),
        memory.getVisibilityType(),
        imageUrls,
        memory.getCreatedAt());
  }
}

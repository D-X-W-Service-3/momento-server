package com.momento.server.domain.memory.dto.response;

import com.momento.server.domain.memory.entity.MemoryImage;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추억에 첨부된 이미지")
public record MemoryImageResponse(
    @Schema(description = "이미지 ID", example = "31") Long imageId,
    @Schema(description = "이미지 URL") String imageUrl,
    @Schema(description = "표시 순서. 0 이 대표 이미지다.", example = "0") int displayOrder) {

  public static MemoryImageResponse from(MemoryImage image) {
    return new MemoryImageResponse(image.getId(), image.getImageUrl(), image.getDisplayOrder());
  }
}

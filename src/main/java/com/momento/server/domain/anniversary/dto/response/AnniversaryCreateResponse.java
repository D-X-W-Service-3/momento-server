package com.momento.server.domain.anniversary.dto.response;

import com.momento.server.domain.anniversary.entity.Anniversary;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "기념일 등록 응답")
public record AnniversaryCreateResponse(
    @Schema(description = "등록된 기념일 ID", example = "7") Long anniversaryId) {

  public static AnniversaryCreateResponse from(Anniversary anniversary) {
    return new AnniversaryCreateResponse(anniversary.getId());
  }
}

package com.momento.server.domain.image.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "이미지 업로드 URL 발급 결과")
public record ImageUploadUrlResponse(
    @Schema(description = "요청한 파일 순서대로 발급된 업로드 정보") List<Upload> uploads) {

  @Schema(description = "파일 하나의 업로드 정보")
  public record Upload(
      @Schema(
              description = "S3 key. 아카이브 등록 · 수정이나 프로필 수정 요청에 그대로 담아 보낸다.",
              example = "memories/1/3f0a5c9e-2b1d-4f7a-8c3e-9d2b7a6e1f00.jpg")
          String imageKey,
      @Schema(description = "presigned PUT URL. Content-Type 헤더를 요청한 값과 같게 보내야 한다.")
          String uploadUrl,
      @Schema(description = "업로드 URL 만료 시각", example = "2026-09-29T21:10:00")
          LocalDateTime expiresAt) {}
}

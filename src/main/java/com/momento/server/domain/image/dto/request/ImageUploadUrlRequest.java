package com.momento.server.domain.image.dto.request;

import com.momento.server.domain.image.entity.ImageTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "이미지 업로드 URL 발급 요청")
public record ImageUploadUrlRequest(
    @Schema(
            description = "이미지 용도. MEMORY(아카이브) / PROFILE(프로필)",
            example = "MEMORY",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "이미지 용도는 필수입니다.")
        ImageTarget target,
    @Schema(description = "올릴 파일 목록. 1~8개", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "올릴 파일을 하나 이상 보내야 합니다.")
        @Size(max = 8, message = "이미지는 한 번에 최대 8개까지 올릴 수 있습니다.")
        List<@NotNull(message = "파일 정보는 비어 있을 수 없습니다.") @Valid UploadFile> files) {

  @Schema(description = "올릴 파일 하나")
  public record UploadFile(
      @Schema(
              description = "image/jpeg / image/png / image/webp / image/heic",
              example = "image/jpeg",
              requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "지원하지 않는 이미지 형식입니다.")
          @Pattern(regexp = "image/(jpeg|png|webp|heic)", message = "지원하지 않는 이미지 형식입니다.")
          String contentType,
      @Schema(
              description = "파일 크기(바이트). 1 이상 20MB(20,971,520) 이하. 이 크기의 파일만 올릴 수 있다.",
              example = "2457600",
              requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull(message = "파일 크기는 필수입니다.")
          @Positive(message = "파일 크기는 1바이트 이상이어야 합니다.")
          // 20MB. 휴대폰 원본 사진(보통 10MB 안팎)은 올라가고 동영상 같은 큰 파일은 막는 크기다.
          @Max(value = 20_971_520, message = "이미지는 20MB 까지 올릴 수 있습니다.")
          Long contentLength) {}
}

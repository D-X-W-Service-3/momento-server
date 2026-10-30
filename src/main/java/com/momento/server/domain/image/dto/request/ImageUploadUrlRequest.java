package com.momento.server.domain.image.dto.request;

import com.momento.server.domain.image.entity.ImageTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
          String contentType) {}
}

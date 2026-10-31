package com.momento.server.domain.image.controller;

import com.momento.server.domain.image.dto.request.ImageUploadUrlRequest;
import com.momento.server.domain.image.dto.response.ImageUploadUrlResponse;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Image", description = "이미지 업로드 API")
public interface ImageApi {

  @Operation(
      summary = "이미지 업로드 URL 발급",
      description =
          "이미지를 S3 에 직접 올릴 수 있는 presigned PUT URL 을 발급한다. 클라이언트는 받은 uploadUrl 로 파일을 PUT 하되 요청한 것과 같은"
              + " Content-Type · Content-Length 헤더를 붙이고, 아카이브 등록 · 수정이나 프로필 수정 요청에 imageKey 를 담아 보낸다. 주소는 10분 뒤 만료된다."
              + " target 은 MEMORY · PROFILE, 파일은 1~8개, 형식은 image/jpeg · image/png · image/webp ·"
              + " image/heic, 크기는 20MB 까지만 받는다. 벗어나면 400 INVALID_INPUT_VALUE 다.")
  CommonResponse<ImageUploadUrlResponse> issueUploadUrls(
      @Parameter(hidden = true) UserPrincipal principal, @Valid ImageUploadUrlRequest request);
}

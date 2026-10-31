package com.momento.server.domain.image.controller;

import com.momento.server.domain.image.dto.request.ImageUploadUrlRequest;
import com.momento.server.domain.image.dto.response.ImageUploadUrlResponse;
import com.momento.server.domain.image.facade.ImageFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestApiController("/v1/images")
@RequiredArgsConstructor
public class ImageController implements ImageApi {

  private final ImageFacade imageFacade;

  @Override
  @PostMapping("/presigned-urls")
  public CommonResponse<ImageUploadUrlResponse> issueUploadUrls(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ImageUploadUrlRequest request) {
    return CommonResponse.ok(imageFacade.issueUploadUrls(principal.getUserId(), request));
  }
}

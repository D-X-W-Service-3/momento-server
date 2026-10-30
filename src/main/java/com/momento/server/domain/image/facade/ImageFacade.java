package com.momento.server.domain.image.facade;

import com.momento.server.domain.image.dto.request.ImageUploadUrlRequest;
import com.momento.server.domain.image.dto.response.ImageUploadUrlResponse;
import com.momento.server.domain.image.service.ImageKeys;
import com.momento.server.global.common.storage.PresignedUpload;
import com.momento.server.global.common.storage.PresignedUrlProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageFacade {

  private final PresignedUrlProvider presignedUrlProvider;

  /** 파일마다 새 key 를 정하고 그 key 로 올릴 주소를 발급한다. 저장하는 것은 없다. */
  public ImageUploadUrlResponse issueUploadUrls(Long userId, ImageUploadUrlRequest request) {
    List<ImageUploadUrlResponse.Upload> uploads =
        request.files().stream()
            .map(
                file -> {
                  String key = ImageKeys.newKey(request.target(), userId, file.contentType());
                  PresignedUpload upload = presignedUrlProvider.uploadUrl(key, file.contentType());
                  return new ImageUploadUrlResponse.Upload(key, upload.url(), upload.expiresAt());
                })
            .toList();

    return new ImageUploadUrlResponse(uploads);
  }
}

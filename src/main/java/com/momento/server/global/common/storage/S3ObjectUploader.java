package com.momento.server.global.common.storage;

import com.momento.server.global.common.property.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * 서버가 가진 파일을 비공개 버킷에 직접 올린다. 클라이언트가 고른 사진은 presigned URL 로 S3 에 바로 올라가지만({@link
 * PresignedUrlProvider}), AI 이미지처럼 서버가 받아 온 파일은 서버가 올린다.
 *
 * <p>실패하면 SDK 예외({@code SdkException})를 그대로 던진다. 다시 시도할지는 호출하는 쪽이 정한다.
 */
@Component
@RequiredArgsConstructor
public class S3ObjectUploader {

  private final S3Client s3Client;
  private final S3Properties s3Properties;

  public void upload(String key, byte[] content, String contentType) {
    s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(s3Properties.getBucket())
            .key(key)
            .contentType(contentType)
            .contentLength((long) content.length)
            .build(),
        RequestBody.fromBytes(content));
  }
}

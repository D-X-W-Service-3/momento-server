package com.momento.server.global.common.storage;

import com.momento.server.global.common.property.S3Properties;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * 비공개 S3 버킷의 객체에 쓰는 presigned URL 을 발급한다. 파일은 클라이언트가 S3 와 직접 주고받고, 서버는 정해진 시간 동안만 쓸 수 있는 주소만 만든다.
 *
 * <p>서명은 서버 안에서 계산만 하고 S3 를 호출하지 않는다. 그래서 로컬 · 테스트의 더미 키로도 주소는 만들어진다(그 주소로 실제 업로드는 되지 않는다).
 */
@Component
@RequiredArgsConstructor
public class PresignedUrlProvider {

  /** 업로드용 주소의 유효 시간. 사진을 고르고 올리는 데 충분하고, 새어 나가도 오래 쓰이지 않을 길이다. */
  static final Duration UPLOAD_EXPIRY = Duration.ofMinutes(10);

  /** 조회용 주소의 유효 시간. 화면을 열어 둔 동안 이미지가 깨지지 않을 길이다. */
  static final Duration VIEW_EXPIRY = Duration.ofHours(1);

  private final S3Presigner s3Presigner;
  private final S3Properties s3Properties;

  /**
   * 업로드용 PUT 주소. 서명에 Content-Type 이 들어가므로 클라이언트는 같은 Content-Type 헤더로 올려야 한다. 만료 시각은 서버 기본 시간대(Clock
   * 빈과 같은 시간대)로 돌려준다.
   */
  public PresignedUpload uploadUrl(String key, String contentType) {
    PutObjectRequest putObjectRequest =
        PutObjectRequest.builder()
            .bucket(s3Properties.getBucket())
            .key(key)
            .contentType(contentType)
            .build();

    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(UPLOAD_EXPIRY)
            .putObjectRequest(putObjectRequest)
            .build();

    PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

    return new PresignedUpload(
        presigned.url().toString(),
        LocalDateTime.ofInstant(presigned.expiration(), ZoneId.systemDefault()));
  }

  /** 조회용 GET 주소. */
  public String viewUrl(String key) {
    GetObjectRequest getObjectRequest =
        GetObjectRequest.builder().bucket(s3Properties.getBucket()).key(key).build();

    GetObjectPresignRequest presignRequest =
        GetObjectPresignRequest.builder()
            .signatureDuration(VIEW_EXPIRY)
            .getObjectRequest(getObjectRequest)
            .build();

    return s3Presigner.presignGetObject(presignRequest).url().toString();
  }
}

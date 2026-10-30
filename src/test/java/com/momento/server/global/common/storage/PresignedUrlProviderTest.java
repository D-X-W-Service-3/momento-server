package com.momento.server.global.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.global.common.property.S3Properties;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 서명은 S3 를 호출하지 않고 계산만 하므로 더미 키로 주소 모양을 확인한다. */
class PresignedUrlProviderTest {

  private static final String KEY = "memories/1/3f2b6c1e-8a4d-4f1b-9c3e-2d7a5b6c8e9f.jpg";

  private S3Presigner s3Presigner;
  private PresignedUrlProvider provider;

  @BeforeEach
  void setUp() {
    s3Presigner =
        S3Presigner.builder()
            .region(Region.AP_NORTHEAST_2)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test-access-key", "test-secret-key")))
            .build();

    S3Properties properties = new S3Properties();
    properties.setBucket("momento-test-bucket");

    provider = new PresignedUrlProvider(s3Presigner, properties);
  }

  @AfterEach
  void tearDown() {
    s3Presigner.close();
  }

  @Test
  @DisplayName("업로드 주소는 버킷과 key 를 가리키고 10분 뒤 만료된다")
  void uploadUrl() {
    LocalDateTime before = LocalDateTime.now();

    PresignedUpload upload = provider.uploadUrl(KEY, "image/jpeg");

    assertThat(upload.url())
        .startsWith("https://momento-test-bucket.s3.ap-northeast-2.amazonaws.com/" + KEY + "?")
        .contains("X-Amz-Expires=600")
        .contains("X-Amz-Signature=");
    assertThat(upload.expiresAt())
        .isAfterOrEqualTo(before.plusMinutes(10).withNano(0))
        .isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(10));
  }

  @Test
  @DisplayName("조회 주소는 버킷과 key 를 가리키고 1시간 동안 유효하다")
  void viewUrl() {
    String url = provider.viewUrl(KEY);

    assertThat(url)
        .startsWith("https://momento-test-bucket.s3.ap-northeast-2.amazonaws.com/" + KEY + "?")
        .contains("X-Amz-Expires=3600")
        .contains("X-Amz-Signature=");
  }
}

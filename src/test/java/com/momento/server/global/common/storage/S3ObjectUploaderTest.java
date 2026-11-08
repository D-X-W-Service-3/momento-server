package com.momento.server.global.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.momento.server.global.common.property.S3Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3ObjectUploaderTest {

  @Test
  @DisplayName("버킷 · key · 형식 · 크기를 담아 올린다")
  void upload() {
    S3Client s3Client = mock(S3Client.class);
    S3Properties properties = new S3Properties();
    properties.setBucket("momento-test-bucket");
    S3ObjectUploader uploader = new S3ObjectUploader(s3Client, properties);

    uploader.upload("generated/31/a.jpg", new byte[] {1, 2, 3}, "image/jpeg");

    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(request.capture(), any(RequestBody.class));
    assertThat(request.getValue().bucket()).isEqualTo("momento-test-bucket");
    assertThat(request.getValue().key()).isEqualTo("generated/31/a.jpg");
    assertThat(request.getValue().contentType()).isEqualTo("image/jpeg");
    assertThat(request.getValue().contentLength()).isEqualTo(3L);
  }
}

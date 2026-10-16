package com.momento.server.global.common.config;

import com.momento.server.global.common.property.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** S3 클라이언트를 등록한다. 업로드·조회용 presigned URL 발급은 S3Presigner 가, 객체 삭제 등 직접 호출은 S3Client 가 맡는다. */
@Configuration
@RequiredArgsConstructor
public class S3Config {

  private final S3Properties s3Properties;

  @Bean
  public S3Client s3Client() {
    return S3Client.builder()
        .region(Region.of(s3Properties.getRegion()))
        .credentialsProvider(credentialsProvider())
        .build();
  }

  @Bean
  public S3Presigner s3Presigner() {
    return S3Presigner.builder()
        .region(Region.of(s3Properties.getRegion()))
        .credentialsProvider(credentialsProvider())
        .build();
  }

  private StaticCredentialsProvider credentialsProvider() {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(s3Properties.getAccessKey(), s3Properties.getSecretKey()));
  }
}

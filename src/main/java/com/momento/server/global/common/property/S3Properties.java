package com.momento.server.global.common.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 추억 이미지를 저장하는 S3 버킷 정보. 키 값은 환경변수로 주입한다. */
@Setter
@Getter
@Component
@ConfigurationProperties("aws.s3")
public class S3Properties {

  private String region;
  private String bucket;
  private String accessKey;
  private String secretKey;
}

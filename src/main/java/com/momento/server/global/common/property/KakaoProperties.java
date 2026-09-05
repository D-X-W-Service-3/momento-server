package com.momento.server.global.common.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 카카오 인가 코드를 액세스 토큰으로 교환할 때 쓰는 앱 정보. */
@Setter
@Getter
@Component
@ConfigurationProperties("kakao")
public class KakaoProperties {

  /** 카카오 REST API 키. 프론트가 쓰는 JavaScript 키와 다르다. */
  private String clientId;

  /** 보안 설정에서 활성화한 경우에만 필요하다. 비활성이면 비워 둔다. */
  private String clientSecret;

  /** 프론트가 인가 코드를 받은 주소. 카카오에 등록된 값과 정확히 같아야 한다. */
  private String redirectUri;
}

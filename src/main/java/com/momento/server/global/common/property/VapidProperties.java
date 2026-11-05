package com.momento.server.global.common.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 웹 푸시 VAPID 키쌍. 값은 환경변수로 주입한다.
 *
 * <p>키를 바꾸면 기존 구독이 모두 무효가 된다. 브라우저가 구독할 때 받은 공개키로만 푸시를 받기 때문이다.
 */
@Setter
@Getter
@Component
@ConfigurationProperties("push.vapid")
public class VapidProperties {

  /** P-256 공개키(압축하지 않은 65바이트)의 base64url. 프론트가 구독할 때 쓴다. */
  private String publicKey;

  /** P-256 개인키(32바이트)의 base64url. 서버 밖으로 내보내지 않는다. */
  private String privateKey;

  /** 푸시 서비스가 문제 있을 때 연락할 주소. {@code mailto:} 또는 {@code https:} URL. */
  private String subject;
}

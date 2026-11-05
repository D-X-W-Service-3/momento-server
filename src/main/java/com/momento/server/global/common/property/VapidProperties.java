package com.momento.server.global.common.property;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 웹 푸시 VAPID 키쌍. 값은 환경변수로 주입한다.
 *
 * <p>키를 바꾸면 기존 구독이 모두 무효가 된다. 브라우저가 구독할 때 받은 공개키로만 푸시를 받기 때문이다.
 *
 * <p>형식이 틀리면 기동을 막는다. 환경변수가 빠지면 Spring 이 {@code ${VAPID_PUBLIC_KEY}} 같은 문자열을 그대로 넣어 기동은 되고 푸시만 조용히
 * 실패하기 때문이다.
 */
@Setter
@Getter
@Validated
@Component
@ConfigurationProperties("push.vapid")
public class VapidProperties {

  private static final String BASE64URL = "^[A-Za-z0-9_-]+$";

  /** P-256 공개키(압축하지 않은 65바이트)의 base64url. 프론트가 구독할 때 쓴다. */
  @Pattern(regexp = BASE64URL, message = "push.vapid.public-key 는 base64url 이어야 합니다.")
  private String publicKey;

  /** P-256 개인키(32바이트)의 base64url. 서버 밖으로 내보내지 않는다. */
  @Pattern(regexp = BASE64URL, message = "push.vapid.private-key 는 base64url 이어야 합니다.")
  private String privateKey;

  /** 푸시 서비스가 문제 있을 때 연락할 주소. {@code mailto:} 또는 {@code https:} URL. */
  @Pattern(
      regexp = "^(mailto:|https://).+",
      message = "push.vapid.subject 는 mailto: 또는 https:// 로 시작해야 합니다.")
  private String subject;
}

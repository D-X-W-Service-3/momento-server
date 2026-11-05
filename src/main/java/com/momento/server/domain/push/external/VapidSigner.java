package com.momento.server.domain.push.external;

import com.momento.server.global.common.property.VapidProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * 푸시 서비스에 보낼 VAPID 인증 헤더(RFC 8292)를 만든다.
 *
 * <p>푸시 서비스는 구독할 때 브라우저가 받은 공개키({@code k})와 이 서명이 맞는지 확인한다. 그래서 키쌍을 바꾸면 기존 구독으로는 보낼 수 없다.
 */
@Component
public class VapidSigner {

  /** RFC 8292 는 24시간을 넘지 말라고 한다. 서버 시계가 조금 어긋나도 거절되지 않게 절반으로 둔다. */
  private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

  private final ECPrivateKey privateKey;
  private final String publicKey;
  private final String subject;
  private final Clock clock;

  public VapidSigner(VapidProperties properties, Clock clock) {
    try {
      this.privateKey = EcKeys.privateKey(EcKeys.decodeBase64Url(properties.getPrivateKey()));
      EcKeys.publicKey(EcKeys.decodeBase64Url(properties.getPublicKey()));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("VAPID 키 형식이 올바르지 않습니다. push.vapid 설정을 확인하세요.", e);
    }
    this.publicKey = properties.getPublicKey();
    this.subject = properties.getSubject();
    this.clock = clock;
  }

  /** endpoint 의 origin 을 audience 로 서명한 {@code Authorization} 헤더 값. */
  public String authorization(URI endpoint) {
    String audience = endpoint.getScheme() + "://" + endpoint.getHost();
    Instant now = clock.instant();
    String token =
        Jwts.builder()
            .setAudience(audience)
            .setExpiration(Date.from(now.plus(TOKEN_LIFETIME)))
            .setSubject(subject)
            .signWith(privateKey, SignatureAlgorithm.ES256)
            .compact();
    return "vapid t=" + token + ", k=" + publicKey;
  }
}

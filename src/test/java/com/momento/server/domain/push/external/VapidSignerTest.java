package com.momento.server.domain.push.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.momento.server.global.common.property.VapidProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.net.URI;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VapidSignerTest {

  private static final Instant NOW = Instant.parse("2027-01-01T00:00:00Z");

  @Test
  @DisplayName("endpoint 의 origin 을 audience 로, 12시간 뒤 만료로 ES256 서명하고 공개키를 k 로 붙인다")
  void signsVapidHeader() throws Exception {
    KeyPair keyPair = EcKeys.generate();
    String publicKey = EcKeys.encodeBase64Url(EcKeys.encode((ECPublicKey) keyPair.getPublic()));
    VapidSigner signer = new VapidSigner(properties(keyPair, publicKey), fixedClock());

    String header = signer.authorization(URI.create("https://fcm.googleapis.com/fcm/send/abc?x=1"));

    assertThat(header).startsWith("vapid t=").endsWith(", k=" + publicKey);
    String token = header.substring("vapid t=".length(), header.indexOf(", k="));
    // JWS(ES256) 서명은 DER 이 아니라 r || s 64바이트여야 푸시 서비스가 받는다
    assertThat(EcKeys.decodeBase64Url(token.split("\\.")[2])).hasSize(64);

    Claims claims =
        Jwts.parserBuilder()
            .setSigningKey(keyPair.getPublic())
            .setClock(() -> Date.from(NOW))
            .build()
            .parseClaimsJws(token)
            .getBody();
    assertThat(claims.getAudience()).isEqualTo("https://fcm.googleapis.com");
    assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(12 * 3600));
    assertThat(claims.getSubject()).isEqualTo("mailto:dev@momento.local");
  }

  @Test
  @DisplayName("VAPID 키 형식이 틀리면 기동할 때 실패한다")
  void rejectsMalformedKeys() {
    VapidProperties properties = new VapidProperties();
    properties.setPublicKey("not-a-key");
    properties.setPrivateKey("not-a-key");
    properties.setSubject("mailto:dev@momento.local");

    assertThatThrownBy(() -> new VapidSigner(properties, fixedClock()))
        .isInstanceOf(IllegalStateException.class);
  }

  private VapidProperties properties(KeyPair keyPair, String publicKey) {
    byte[] scalar = ((ECPrivateKey) keyPair.getPrivate()).getS().toByteArray();
    byte[] fixed = new byte[32];
    int length = Math.min(scalar.length, 32);
    System.arraycopy(scalar, scalar.length - length, fixed, 32 - length, length);

    VapidProperties properties = new VapidProperties();
    properties.setPublicKey(publicKey);
    properties.setPrivateKey(EcKeys.encodeBase64Url(fixed));
    properties.setSubject("mailto:dev@momento.local");
    return properties;
  }

  private Clock fixedClock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }
}

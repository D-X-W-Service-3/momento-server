package com.momento.server.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.auth.external.KakaoAuthClient;
import com.momento.server.domain.auth.external.dto.KakaoTokenResponse;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 토큰 교환 요청이 실제로 form-urlencoded 로 나가는지 확인한다.
 *
 * <p>다른 테스트는 이 클라이언트를 목으로 대체하므로 인코딩이 깨져도 드러나지 않는다. 여기서는 실제 HTTP 로 호출해 본문을 확인한다.
 */
@SpringBootTest
class KakaoAuthClientTest {

  private static final HttpServer KAKAO_STUB = createStub();
  private static final AtomicReference<String> LAST_REQUEST_BODY = new AtomicReference<>();
  private static final AtomicReference<String> LAST_CONTENT_TYPE = new AtomicReference<>();

  @Autowired private KakaoAuthClient kakaoAuthClient;

  private static HttpServer createStub() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
      server.createContext(
          "/oauth/token",
          exchange -> {
            LAST_REQUEST_BODY.set(
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            LAST_CONTENT_TYPE.set(exchange.getRequestHeaders().getFirst("Content-Type"));

            byte[] response =
                "{\"access_token\":\"issued-token\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  @DynamicPropertySource
  static void kakaoAuthUrl(DynamicPropertyRegistry registry) {
    registry.add(
        "external.api-url.kakao-auth",
        () -> "http://localhost:" + KAKAO_STUB.getAddress().getPort());
  }

  @Test
  @DisplayName("토큰 교환 요청은 form-urlencoded 본문으로 나가고 액세스 토큰을 읽어온다")
  void sendsFormUrlEncodedBody() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", "rest-api-key");
    form.add("redirect_uri", "http://localhost:3000/auth/kakao/callback");
    form.add("code", "authorization-code");

    KakaoTokenResponse response = kakaoAuthClient.issueToken(form);

    assertThat(response.accessToken()).isEqualTo("issued-token");
    assertThat(LAST_CONTENT_TYPE.get()).startsWith("application/x-www-form-urlencoded");
    assertThat(LAST_REQUEST_BODY.get())
        .contains("grant_type=authorization_code")
        .contains("client_id=rest-api-key")
        .contains("code=authorization-code");
  }
}

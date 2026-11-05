package com.momento.server.domain.push.external;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * JDK HttpClient 로 푸시 서비스에 직접 보낸다.
 *
 * <p>요청 형식은 RFC 8030(전송) · 8291(암호화) · 8292(VAPID) 를 따른다. 응답 본문은 쓰지 않는다.
 */
@Component
@RequiredArgsConstructor
public class JdkWebPushClient implements WebPushClient {

  /** 기기가 꺼져 있으면 푸시 서비스가 이 시간(초)까지 보관한다. 하루 지난 알림은 인앱 목록으로 충분하다. */
  private static final String TTL_SECONDS = "86400";

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

  private final VapidSigner vapidSigner;

  @Override
  public int send(String endpoint, String p256dh, String auth, byte[] payload) {
    URI uri = URI.create(endpoint);
    byte[] body;
    try {
      body =
          WebPushEncryption.encrypt(
              EcKeys.decodeBase64Url(p256dh), EcKeys.decodeBase64Url(auth), payload);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new WebPushException("푸시 페이로드를 암호화하지 못했습니다.", e);
    }

    HttpRequest request =
        HttpRequest.newBuilder(uri)
            .timeout(REQUEST_TIMEOUT)
            .header("Authorization", vapidSigner.authorization(uri))
            .header("TTL", TTL_SECONDS)
            .header("Urgency", "normal")
            .header("Content-Encoding", "aes128gcm")
            .header("Content-Type", "application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
    try {
      return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    } catch (IOException e) {
      throw new WebPushException("푸시 서비스에 요청하지 못했습니다.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new WebPushException("푸시 요청이 중단되었습니다.", e);
    }
  }
}

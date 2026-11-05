package com.momento.server.domain.push.entity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * 구독으로 받을 수 있는 endpoint 규칙.
 *
 * <p>서버는 푸시를 보낼 때 endpoint 로 직접 요청한다. 아무 주소나 받으면 회원이 서버 내부 주소(예: EC2 메타데이터)를 등록해 서버가 그곳으로 요청하게 만들 수
 * 있다. 그래서 https 이면서 주요 브라우저의 푸시 서비스 호스트인 주소만 받는다.
 */
public final class PushEndpoint {

  /** Chrome · Samsung 인터넷(FCM), Firefox, Safari · iOS PWA. */
  private static final Set<String> HOSTS =
      Set.of("fcm.googleapis.com", "updates.push.services.mozilla.com", "web.push.apple.com");

  /** Edge(WNS). 호스트가 지역마다 달라(예: {@code wns2-par02p.notify.windows.com}) 도메인 끝으로 비교한다. */
  private static final String HOST_SUFFIX = ".notify.windows.com";

  private PushEndpoint() {}

  public static boolean isAllowed(String endpoint) {
    if (endpoint == null) {
      return false;
    }
    URI uri;
    try {
      uri = new URI(endpoint);
    } catch (URISyntaxException e) {
      return false;
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || uri.getRawUserInfo() != null
        || (uri.getPort() != -1 && uri.getPort() != 443)
        || uri.getHost() == null) {
      return false;
    }
    String host = uri.getHost().toLowerCase(Locale.ROOT);
    return HOSTS.contains(host) || host.endsWith(HOST_SUFFIX);
  }
}

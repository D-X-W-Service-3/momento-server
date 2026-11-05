package com.momento.server.domain.push.external;

/** 구독 하나로 웹 푸시 한 건을 보낸다. 테스트에서는 실제 푸시 서비스를 부르지 않도록 이 인터페이스를 가짜로 바꾼다. */
public interface WebPushClient {

  /**
   * @param payload 암호화하기 전의 페이로드
   * @return 푸시 서비스의 HTTP 응답 코드. 성공은 201 이고, 404 · 410 은 구독이 사라졌다는 뜻이다.
   * @throws WebPushException 암호화에 실패했거나 푸시 서비스에 닿지 못한 경우
   */
  int send(String endpoint, String p256dh, String auth, byte[] payload);
}

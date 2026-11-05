package com.momento.server.domain.push.external;

/** 푸시 한 건을 보내지 못했다. 응답으로 돌려줄 일이 없어 {@code ApiException} 이 아니라 로그로만 남긴다. */
public class WebPushException extends RuntimeException {

  public WebPushException(String message, Throwable cause) {
    super(message, cause);
  }
}

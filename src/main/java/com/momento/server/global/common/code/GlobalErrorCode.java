package com.momento.server.global.common.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {
  INTERNAL_SERVER_ERROR("응답 처리 중, 예외가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR),
  INVALID_INPUT_VALUE("요청 값이 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
  INVALID_ACCESS_TOKEN("유효하지 않은 토큰이거나, 토큰의 유효기한이 만료되었습니다.", HttpStatus.UNAUTHORIZED),
  ;

  private final String message;
  private final HttpStatus status;
}

package com.momento.server.domain.auth.exception;

import com.momento.server.global.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {
  INVALID_KAKAO_CODE("카카오 인가 코드가 유효하지 않습니다.", HttpStatus.UNAUTHORIZED),
  INVALID_KAKAO_TOKEN("카카오 액세스 토큰이 유효하지 않습니다.", HttpStatus.UNAUTHORIZED),
  KAKAO_SERVER_ERROR("카카오 서버와 통신하지 못했습니다.", HttpStatus.BAD_GATEWAY),
  ;

  private final String message;
  private final HttpStatus status;
}

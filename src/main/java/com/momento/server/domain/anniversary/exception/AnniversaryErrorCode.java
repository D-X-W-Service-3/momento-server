package com.momento.server.domain.anniversary.exception;

import com.momento.server.global.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnniversaryErrorCode implements ErrorCode {
  /** 없는 기념일과 다른 회원의 기념일을 구분하지 않는다. 구분하면 순번 ID 를 대입하는 것만으로 기념일 존재 여부가 드러난다. */
  ANNIVERSARY_NOT_FOUND("기념일을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  ;

  private final String message;
  private final HttpStatus status;
}

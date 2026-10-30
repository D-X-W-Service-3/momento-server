package com.momento.server.domain.user.exception;

import com.momento.server.global.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {
  USER_NOT_FOUND("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  NICKNAME_DUPLICATED("이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),
  ;

  private final String message;
  private final HttpStatus status;
}

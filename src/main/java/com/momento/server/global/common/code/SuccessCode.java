package com.momento.server.global.common.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SuccessCode {
  OK(HttpStatus.OK),
  CREATED(HttpStatus.CREATED),
  NO_CONTENT(HttpStatus.NO_CONTENT);

  private final HttpStatus status;

  public static final String MESSAGE = "요청에 성공했습니다.";
  public static final String CODE = "SUCCESS";
}

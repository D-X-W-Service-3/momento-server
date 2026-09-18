package com.momento.server.domain.timecapsule.exception;

import com.momento.server.global.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CapsuleErrorCode implements ErrorCode {
  /** 없는 캡슐, 삭제된 캡슐, 참여하지 않은 캡슐을 구분하지 않는다. 구분하면 순번 ID 를 대입하는 것만으로 캡슐 존재 여부가 드러난다. */
  CAPSULE_NOT_FOUND("타임캡슐을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  ;

  private final String message;
  private final HttpStatus status;
}

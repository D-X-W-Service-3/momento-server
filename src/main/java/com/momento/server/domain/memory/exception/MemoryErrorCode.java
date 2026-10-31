package com.momento.server.domain.memory.exception;

import com.momento.server.global.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemoryErrorCode implements ErrorCode {
  /** 없는 아카이브, 삭제된 아카이브, 다른 회원의 아카이브를 구분하지 않는다. 구분하면 순번 ID 를 대입하는 것만으로 존재 여부가 드러난다. */
  MEMORY_NOT_FOUND("아카이브를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

  /**
   * 내 경로에 발급한 key 가 아니거나, 같은 key 가 두 번 있거나, 대표 사진 key 가 사진 목록에 없는 경우다. 남이 올린 사진의 key 를 내 아카이브에 붙이지
   * 못하게 막는다.
   */
  INVALID_IMAGE_KEY("업로드한 이미지 정보가 올바르지 않습니다.", HttpStatus.BAD_REQUEST),
  ;

  private final String message;
  private final HttpStatus status;
}

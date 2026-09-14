package com.momento.server.domain.letter.exception;

import com.momento.server.global.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LetterErrorCode implements ErrorCode {
  LETTER_NOT_FOUND("작성한 편지를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
  LETTER_ALREADY_EXISTS("이미 작성한 편지가 있습니다.", HttpStatus.CONFLICT),
  LETTER_WRITING_CLOSED("편지를 작성할 수 있는 기간이 아닙니다.", HttpStatus.CONFLICT);

  private final String message;
  private final HttpStatus status;
}

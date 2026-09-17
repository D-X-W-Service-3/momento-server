package com.momento.server.global.common.exception;

import com.momento.server.global.common.code.ErrorCode;
import com.momento.server.global.common.code.GlobalErrorCode;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(ApiException.class)
  public CommonResponse<?> handleApiException(ApiException exception) {
    ErrorCode errorCode = exception.getErrorCode();

    log.error("에러 발생: ({}) {}", errorCode.name(), errorCode.getMessage());

    return CommonResponse.error(errorCode);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public CommonResponse<?> handleValidationException(MethodArgumentNotValidException exception) {
    ErrorCode errorCode = GlobalErrorCode.INVALID_INPUT_VALUE;
    String message =
        Optional.ofNullable(exception.getBindingResult().getFieldError())
            .map(FieldError::getDefaultMessage)
            .orElse(errorCode.getMessage());

    log.warn("요청 값 검증 실패: {}", message);

    return new CommonResponse<>(errorCode.getStatus().value(), errorCode.name(), message, null);
  }

  /**
   * 깨진 JSON, enum 에 없는 값, 타입이 맞지 않는 경로 변수처럼 요청을 읽는 단계에서 실패한 경우. 클라이언트 입력 문제이므로 500 이 아니라 400 으로
   * 응답한다. 예외 메시지에는 내부 클래스명이 섞여 있어 응답에는 싣지 않는다.
   */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public CommonResponse<?> handleUnreadableRequest(Exception exception) {
    ErrorCode errorCode = GlobalErrorCode.INVALID_INPUT_VALUE;

    log.warn("요청을 읽을 수 없음: {}", exception.getMessage());

    return CommonResponse.error(errorCode);
  }

  @ExceptionHandler(Exception.class)
  public CommonResponse<?> handleException(Exception exception, HttpServletRequest request) {
    ErrorCode errorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR;
    HttpStatus httpStatus = errorCode.getStatus();

    log.error("예상치 못한 예외 발생", exception);

    return CommonResponse.error(exception, httpStatus);
  }
}

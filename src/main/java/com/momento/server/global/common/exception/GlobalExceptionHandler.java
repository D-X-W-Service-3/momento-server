package com.momento.server.global.common.exception;

import com.momento.server.global.common.code.ErrorCode;
import com.momento.server.global.common.code.GlobalErrorCode;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

  @ExceptionHandler(Exception.class)
  public CommonResponse<?> handleException(Exception exception, HttpServletRequest request) {
    ErrorCode errorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR;
    HttpStatus httpStatus = errorCode.getStatus();

    log.error("예상치 못한 예외 발생", exception);

    return CommonResponse.error(exception, httpStatus);
  }
}

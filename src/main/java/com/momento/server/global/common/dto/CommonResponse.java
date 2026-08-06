package com.momento.server.global.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.momento.server.global.common.code.ErrorCode;
import com.momento.server.global.common.code.GlobalErrorCode;
import com.momento.server.global.common.code.SuccessCode;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "공통 응답 DTO")
public record CommonResponse<T>(
    @Schema(description = "HTTP 응답 코드", example = "200") int status,
    @Schema(description = "에러/성공 코드", example = "SUCCESS") String code,
    @Schema(description = "에러 발생 시 메시지", example = "요청에 성공했습니다.") String message,
    @JsonInclude(value = JsonInclude.Include.NON_NULL) T data) {
  public static CommonResponse<?> ok() {
    return success(SuccessCode.OK);
  }

  public static <T> CommonResponse<T> ok(final T data) {
    return success(SuccessCode.OK, data);
  }

  public static <T> CommonResponse<T> success(final SuccessCode successCode) {
    return new CommonResponse<>(
        successCode.getStatus().value(), SuccessCode.CODE, SuccessCode.MESSAGE, null);
  }

  public static <T> CommonResponse<T> success(final SuccessCode successCode, final T data) {
    return new CommonResponse<>(
        successCode.getStatus().value(), SuccessCode.CODE, SuccessCode.MESSAGE, data);
  }

  public static CommonResponse<Void> error(final ErrorCode errorCode) {
    return new CommonResponse<>(
        errorCode.getStatus().value(), errorCode.name(), errorCode.getMessage(), null);
  }

  public static CommonResponse<Void> error(final Exception exception, HttpStatus status) {
    return new CommonResponse<>(status.value(), status.name(), exception.getMessage(), null);
  }

  public static CommonResponse<Void> error(final Exception exception) {
    GlobalErrorCode errorCode = GlobalErrorCode.INTERNAL_SERVER_ERROR;
    String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();

    return new CommonResponse<>(errorCode.getStatus().value(), errorCode.name(), message, null);
  }
}

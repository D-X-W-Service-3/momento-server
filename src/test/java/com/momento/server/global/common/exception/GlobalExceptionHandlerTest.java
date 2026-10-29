package com.momento.server.global.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.global.common.dto.CommonResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/** 요청을 읽는 단계의 실패가 포괄 핸들러(500)로 떨어지지 않는지, 응답 메시지에 내부 정보가 섞이지 않는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("JSON 이 깨진 요청은 500 이 아니라 400 으로 응답한다")
  void malformedJsonIsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON).content("{\"code\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."));
  }

  @Test
  @DisplayName("검증 실패는 필드의 검증 메시지를 싣는다")
  void validationFailureKeepsMessage() {
    CommonResponse<?> response =
        new GlobalExceptionHandler()
            .handleValidationException(failure(false, "월은 1 이상 12 이하여야 합니다."));

    assertThat(response.code()).isEqualTo("INVALID_INPUT_VALUE");
    assertThat(response.message()).isEqualTo("월은 1 이상 12 이하여야 합니다.");
  }

  @Test
  @DisplayName("쿼리 파라미터 타입 변환 실패는 내부 클래스명이 담긴 메시지 대신 기본 메시지를 싣는다")
  void bindingFailureHidesMessage() {
    CommonResponse<?> response =
        new GlobalExceptionHandler()
            .handleValidationException(
                failure(
                    true, "Failed to convert value of type 'java.lang.String' to required type"));

    assertThat(response.code()).isEqualTo("INVALID_INPUT_VALUE");
    assertThat(response.message()).isEqualTo("요청 값이 올바르지 않습니다.");
  }

  private static MethodArgumentNotValidException failure(boolean bindingFailure, String message) {
    BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");
    result.addError(new FieldError("request", "type", "ALL", bindingFailure, null, null, message));
    return new MethodArgumentNotValidException(null, result);
  }
}

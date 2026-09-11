package com.momento.server.global.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 요청을 읽는 단계의 실패가 포괄 핸들러(500)로 떨어지지 않는지 검증한다. */
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
}

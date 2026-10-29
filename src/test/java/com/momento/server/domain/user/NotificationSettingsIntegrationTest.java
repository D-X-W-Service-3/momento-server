package com.momento.server.domain.user;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/** 알림 설정 API 를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationSettingsIntegrationTest {

  private static final String PATH = "/v1/users/me/notification-settings";

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private User me;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = userRepository.save(User.builder().kakaoId("settings-me").nickname("나").build());
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("새 회원은 캡슐 · 참여자 알림이 켜져 있고 추억 다시보기가 꺼져 있다")
  void getsDefaults() throws Exception {
    authorized(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.capsuleOpened").value(true))
        .andExpect(jsonPath("$.data.capsuleDDay").value(true))
        .andExpect(jsonPath("$.data.letterDeadline").value(true))
        .andExpect(jsonPath("$.data.newMember").value(true))
        .andExpect(jsonPath("$.data.memoryReminder").value(false));
  }

  @Test
  @DisplayName("저장된 설정을 그대로 돌려준다")
  void getsSavedSettings() throws Exception {
    transactionTemplate.executeWithoutResult(
        status ->
            userRepository
                .findById(me.getId())
                .orElseThrow()
                .getNotificationSettings()
                .update(false, false, null, null, true));

    authorized(get(PATH))
        .andExpect(jsonPath("$.data.capsuleOpened").value(false))
        .andExpect(jsonPath("$.data.capsuleDDay").value(false))
        .andExpect(jsonPath("$.data.letterDeadline").value(true))
        .andExpect(jsonPath("$.data.newMember").value(true))
        .andExpect(jsonPath("$.data.memoryReminder").value(true));
  }

  @Test
  @DisplayName("탈퇴한 회원의 토큰은 인증 필터에서 401 이다")
  void withdrawnUserIsUnauthorized() throws Exception {
    transactionTemplate.executeWithoutResult(
        status -> userRepository.findById(me.getId()).orElseThrow().withdraw());

    authorized(get(PATH))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  @DisplayName("인증이 없으면 401 이다")
  void getRequiresAuthentication() throws Exception {
    mockMvc
        .perform(get(PATH))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  private ResultActions authorized(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.header(
            AUTHORIZATION, "Bearer " + tokenProvider.generateToken(me, Duration.ofHours(1))));
  }

  private void cleanUp() {
    userRepository.deleteAllInBatch();
  }
}

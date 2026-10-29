package com.momento.server.domain.anniversary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.anniversary.entity.RepeatType;
import com.momento.server.domain.anniversary.repository.AnniversaryRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 기념일 API 를 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AnniversaryIntegrationTest {

  private static final String FULL_BODY =
      """
      {"title": "우리 100일", "anniversaryDate": "2026-08-12", "repeatType": "YEARLY",
       "memo": "처음 만난 카페에 다시 가기", "notificationEnabled": true,
       "notifyTime": "20:30", "remindDaysBefore": 3}
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private AnniversaryRepository anniversaryRepository;

  private User me;

  @BeforeEach
  void setUp() {
    cleanUp();
    me = userRepository.save(User.builder().kakaoId("anniversary-me").nickname("나").build());
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @Test
  @DisplayName("등록하면 201 과 ID 를 돌려주고 모든 값을 저장한다")
  void creates() throws Exception {
    String response =
        create(FULL_BODY)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.anniversaryId").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Anniversary saved = anniversaryRepository.findById(idOf(response)).orElseThrow();
    assertThat(saved.getUser().getId()).isEqualTo(me.getId());
    assertThat(saved.getTitle()).isEqualTo("우리 100일");
    assertThat(saved.getAnniversaryDate()).isEqualTo(LocalDate.of(2026, 8, 12));
    assertThat(saved.getRepeatType()).isEqualTo(RepeatType.YEARLY);
    assertThat(saved.getMemo()).isEqualTo("처음 만난 카페에 다시 가기");
    assertThat(saved.isNotificationEnabled()).isTrue();
    assertThat(saved.getNotifyTime()).isEqualTo(LocalTime.of(20, 30));
    assertThat(saved.getRemindDaysBefore()).isEqualTo(3);
  }

  @Test
  @DisplayName("선택값을 보내지 않으면 알림 시각은 09:00, 메모 · 미리 알림은 없다")
  void createsWithDefaults() throws Exception {
    String response =
        create(
                """
                {"title": "첫 만남", "anniversaryDate": "2026-05-04", "repeatType": "NONE",
                 "notificationEnabled": true}
                """)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Anniversary saved = anniversaryRepository.findById(idOf(response)).orElseThrow();
    assertThat(saved.getNotifyTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(saved.getMemo()).isNull();
    assertThat(saved.getRemindDaysBefore()).isNull();
  }

  @Test
  @DisplayName("알림 받기를 끄면 미리 알림은 저장하지 않고 알림 시각은 보관한다")
  void disabledNotificationDropsRemind() throws Exception {
    String response =
        create(FULL_BODY.replace("\"notificationEnabled\": true", "\"notificationEnabled\": false"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Anniversary saved = anniversaryRepository.findById(idOf(response)).orElseThrow();
    assertThat(saved.isNotificationEnabled()).isFalse();
    assertThat(saved.getNotifyTime()).isEqualTo(LocalTime.of(20, 30));
    assertThat(saved.getRemindDaysBefore()).isNull();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "{\"title\": \"   \", \"anniversaryDate\": \"2026-08-12\", \"repeatType\": \"YEARLY\","
            + " \"notificationEnabled\": true}",
        "{\"title\": \"우리 100일\", \"anniversaryDate\": \"2026-08-12\", \"repeatType\": \"YEARLY\"}",
        "{\"title\": \"우리 100일\", \"anniversaryDate\": \"2026-08-12\", \"repeatType\": \"WEEKLY\","
            + " \"notificationEnabled\": true}",
        "{\"title\": \"우리 100일\", \"repeatType\": \"YEARLY\", \"notificationEnabled\": true}",
      })
  @DisplayName("필수값이 없거나 형식이 맞지 않으면 400 이다")
  void rejectsInvalidBody(String body) throws Exception {
    create(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    assertThat(anniversaryRepository.count()).isZero();
  }

  @Test
  @DisplayName("미리 알림이 1 · 3 · 5 · 7 · 10 이 아니면 400 이다")
  void rejectsRemindDays() throws Exception {
    create(FULL_BODY.replace("\"remindDaysBefore\": 3", "\"remindDaysBefore\": 2"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
        .andExpect(jsonPath("$.message").value("미리 알림은 1, 3, 5, 7, 10일 전 중에서 선택해야 합니다."));
  }

  @Test
  @DisplayName("인증이 없으면 401 이다")
  void createRequiresAuthentication() throws Exception {
    mockMvc
        .perform(
            post("/v1/anniversaries").contentType(MediaType.APPLICATION_JSON).content(FULL_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    assertThat(anniversaryRepository.count()).isZero();
  }

  private ResultActions create(String body) throws Exception {
    return authorized(
        post("/v1/anniversaries").contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private ResultActions authorized(MockHttpServletRequestBuilder request) throws Exception {
    return mockMvc.perform(
        request.header(
            AUTHORIZATION, "Bearer " + tokenProvider.generateToken(me, Duration.ofHours(1))));
  }

  private static Long idOf(String response) {
    return ((Number) JsonPath.read(response, "$.data.anniversaryId")).longValue();
  }

  private void cleanUp() {
    anniversaryRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}

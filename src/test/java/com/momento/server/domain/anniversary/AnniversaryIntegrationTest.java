package com.momento.server.domain.anniversary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 기념일 API 를 검증한다. 오늘은 2026-10-29 로 고정한다. */
@SpringBootTest
@AutoConfigureMockMvc
class AnniversaryIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-29T03:00:00Z");

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

  @MockitoBean private Clock clock;

  private User me;

  @BeforeEach
  void setUp() {
    cleanUp();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
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

  @Test
  @DisplayName("상세는 저장한 값과 다음 기념일 날짜 · 남은 일수를 돌려준다")
  void getsDetail() throws Exception {
    Anniversary anniversary =
        save(
            me,
            LocalDate.of(2026, 8, 12),
            RepeatType.YEARLY,
            "처음 만난 카페에 다시 가기",
            true,
            LocalTime.of(20, 30),
            3);

    authorized(get("/v1/anniversaries/{anniversaryId}", anniversary.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.anniversaryId").value(anniversary.getId()))
        .andExpect(jsonPath("$.data.title").value("우리 100일"))
        .andExpect(jsonPath("$.data.anniversaryDate").value("2026-08-12"))
        .andExpect(jsonPath("$.data.repeatType").value("YEARLY"))
        .andExpect(jsonPath("$.data.memo").value("처음 만난 카페에 다시 가기"))
        .andExpect(jsonPath("$.data.notificationEnabled").value(true))
        .andExpect(jsonPath("$.data.notifyTime").value("20:30"))
        .andExpect(jsonPath("$.data.remindDaysBefore").value(3))
        .andExpect(jsonPath("$.data.nextDate").value("2027-08-12"))
        .andExpect(jsonPath("$.data.dDay").value(287));
  }

  @Test
  @DisplayName("지난 반복 없는 기념일은 그 날짜 그대로이고 남은 일수가 음수다. 비어 있는 값은 null 이다")
  void getsPastOneTime() throws Exception {
    Anniversary anniversary =
        save(me, LocalDate.of(2026, 10, 19), RepeatType.NONE, null, false, null, null);

    authorized(get("/v1/anniversaries/{anniversaryId}", anniversary.getId()))
        .andExpect(jsonPath("$.data.nextDate").value("2026-10-19"))
        .andExpect(jsonPath("$.data.dDay").value(-10))
        .andExpect(jsonPath("$.data.memo").value(nullValue()))
        .andExpect(jsonPath("$.data.notifyTime").value("09:00"))
        .andExpect(jsonPath("$.data.remindDaysBefore").value(nullValue()));
  }

  @Test
  @DisplayName("오늘이 기념일이면 남은 일수가 0 이다")
  void getsToday() throws Exception {
    Anniversary anniversary =
        save(me, LocalDate.of(2020, 10, 29), RepeatType.YEARLY, null, true, null, null);

    authorized(get("/v1/anniversaries/{anniversaryId}", anniversary.getId()))
        .andExpect(jsonPath("$.data.nextDate").value("2026-10-29"))
        .andExpect(jsonPath("$.data.dDay").value(0));
  }

  @Test
  @DisplayName("다른 회원의 기념일과 없는 기념일은 404 다")
  void detailHidesOthersAndMissing() throws Exception {
    User other =
        userRepository.save(User.builder().kakaoId("anniversary-other").nickname("남").build());
    Anniversary others =
        save(other, LocalDate.of(2026, 8, 12), RepeatType.YEARLY, null, false, null, null);

    authorized(get("/v1/anniversaries/{anniversaryId}", others.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ANNIVERSARY_NOT_FOUND"));
    authorized(get("/v1/anniversaries/{anniversaryId}", others.getId() + 1))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ANNIVERSARY_NOT_FOUND"));
  }

  @Test
  @DisplayName("상세도 인증이 없으면 401 이다")
  void detailRequiresAuthentication() throws Exception {
    Anniversary anniversary =
        save(me, LocalDate.of(2026, 8, 12), RepeatType.YEARLY, null, false, null, null);

    mockMvc
        .perform(get("/v1/anniversaries/{anniversaryId}", anniversary.getId()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  @DisplayName("수정은 모든 값을 바꾸고 상세와 같은 형태를 돌려준다")
  void updates() throws Exception {
    Anniversary anniversary =
        save(me, LocalDate.of(2026, 5, 4), RepeatType.NONE, null, false, null, null);

    update(anniversary, FULL_BODY)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.anniversaryId").value(anniversary.getId()))
        .andExpect(jsonPath("$.data.title").value("우리 100일"))
        .andExpect(jsonPath("$.data.notifyTime").value("20:30"))
        .andExpect(jsonPath("$.data.nextDate").value("2027-08-12"))
        .andExpect(jsonPath("$.data.dDay").value(287));

    Anniversary updated = anniversaryRepository.findById(anniversary.getId()).orElseThrow();
    assertThat(updated.getAnniversaryDate()).isEqualTo(LocalDate.of(2026, 8, 12));
    assertThat(updated.getRepeatType()).isEqualTo(RepeatType.YEARLY);
    assertThat(updated.getMemo()).isEqualTo("처음 만난 카페에 다시 가기");
    assertThat(updated.isNotificationEnabled()).isTrue();
    assertThat(updated.getNotifyTime()).isEqualTo(LocalTime.of(20, 30));
    assertThat(updated.getRemindDaysBefore()).isEqualTo(3);
  }

  @Test
  @DisplayName("수정에서 보내지 않은 선택값은 지워지고 알림 시각은 09:00 이 된다")
  void updateReplacesOmittedValues() throws Exception {
    Anniversary anniversary =
        save(me, LocalDate.of(2026, 8, 12), RepeatType.YEARLY, "메모", true, LocalTime.of(20, 30), 3);

    update(
            anniversary,
            """
            {"title": "우리 100일", "anniversaryDate": "2026-08-12", "repeatType": "YEARLY",
             "memo": null, "notificationEnabled": true, "remindDaysBefore": null}
            """)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.memo").value(nullValue()))
        .andExpect(jsonPath("$.data.notifyTime").value("09:00"))
        .andExpect(jsonPath("$.data.remindDaysBefore").value(nullValue()));

    Anniversary updated = anniversaryRepository.findById(anniversary.getId()).orElseThrow();
    assertThat(updated.getMemo()).isNull();
    assertThat(updated.getNotifyTime()).isEqualTo(LocalTime.of(9, 0));
    assertThat(updated.getRemindDaysBefore()).isNull();
  }

  @Test
  @DisplayName("수정에서 알림 받기를 끄면 미리 알림은 지우고 알림 시각은 보관한다")
  void updateDisablesNotification() throws Exception {
    Anniversary anniversary =
        save(me, LocalDate.of(2026, 8, 12), RepeatType.YEARLY, null, true, LocalTime.of(9, 0), 1);

    update(
            anniversary,
            FULL_BODY.replace("\"notificationEnabled\": true", "\"notificationEnabled\": false"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.notificationEnabled").value(false))
        .andExpect(jsonPath("$.data.notifyTime").value("20:30"))
        .andExpect(jsonPath("$.data.remindDaysBefore").value(nullValue()));
  }

  @Test
  @DisplayName("수정도 등록과 같은 규칙으로 검증하고, 실패하면 바꾸지 않는다")
  void updateValidatesLikeCreate() throws Exception {
    Anniversary anniversary =
        save(me, LocalDate.of(2026, 5, 4), RepeatType.NONE, "메모", true, null, 1);

    update(anniversary, FULL_BODY.replace("\"remindDaysBefore\": 3", "\"remindDaysBefore\": 2"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    update(anniversary, FULL_BODY.replace("\"우리 100일\"", "\"  \""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

    Anniversary unchanged = anniversaryRepository.findById(anniversary.getId()).orElseThrow();
    assertThat(unchanged.getAnniversaryDate()).isEqualTo(LocalDate.of(2026, 5, 4));
    assertThat(unchanged.getRemindDaysBefore()).isEqualTo(1);
  }

  @Test
  @DisplayName("다른 회원의 기념일과 없는 기념일은 수정할 수 없고 404 다")
  void updateHidesOthersAndMissing() throws Exception {
    User other =
        userRepository.save(User.builder().kakaoId("anniversary-other").nickname("남").build());
    Anniversary others =
        save(other, LocalDate.of(2026, 5, 4), RepeatType.NONE, null, false, null, null);

    update(others, FULL_BODY)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ANNIVERSARY_NOT_FOUND"));
    authorized(
            patch("/v1/anniversaries/{anniversaryId}", others.getId() + 1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(FULL_BODY))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ANNIVERSARY_NOT_FOUND"));

    assertThat(anniversaryRepository.findById(others.getId()).orElseThrow().getAnniversaryDate())
        .isEqualTo(LocalDate.of(2026, 5, 4));
  }

  @Test
  @DisplayName("수정도 인증이 없으면 401 이다")
  void updateRequiresAuthentication() throws Exception {
    Anniversary anniversary =
        save(me, LocalDate.of(2026, 5, 4), RepeatType.NONE, null, false, null, null);

    mockMvc
        .perform(
            patch("/v1/anniversaries/{anniversaryId}", anniversary.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(FULL_BODY))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  private ResultActions update(Anniversary anniversary, String body) throws Exception {
    return authorized(
        patch("/v1/anniversaries/{anniversaryId}", anniversary.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private Anniversary save(
      User user,
      LocalDate date,
      RepeatType repeatType,
      String memo,
      boolean notificationEnabled,
      LocalTime notifyTime,
      Integer remindDaysBefore) {
    return anniversaryRepository.save(
        Anniversary.create(
            user,
            "우리 100일",
            date,
            repeatType,
            memo,
            notificationEnabled,
            notifyTime,
            remindDaysBefore));
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

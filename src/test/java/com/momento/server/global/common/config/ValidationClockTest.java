package com.momento.server.global.common.config;

import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 요청 검증의 {@code @Future} 가 시스템 시계가 아니라 {@link Clock} 빈을 기준으로 판단하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
class ValidationClockTest {

  private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

  @Autowired private MockMvc mockMvc;
  @Autowired private TokenProvider tokenProvider;
  @Autowired private UserRepository userRepository;
  @Autowired private TimeCapsuleRepository timeCapsuleRepository;
  @Autowired private CapsuleMemberRepository capsuleMemberRepository;

  @MockitoBean private Clock clock;

  private String token;

  @BeforeEach
  void setUp() {
    cleanUp();
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    User user = userRepository.save(User.builder().kakaoId("clock-user").nickname("상래").build());
    token = tokenProvider.generateToken(user, Duration.ofHours(1));
  }

  @AfterEach
  void tearDown() {
    cleanUp();
  }

  @ParameterizedTest(name = "고정된 현재 시각 기준 {0}초 → {1}")
  @CsvSource({"1, 201", "0, 400", "-1, 400"})
  @DisplayName("캡슐 공개 일시는 Clock 빈이 가리키는 현재 시각보다 뒤여야 한다")
  void openAtIsJudgedByClockBean(long offsetSeconds, int expectedStatus) throws Exception {
    String openAt =
        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusSeconds(offsetSeconds).toString();

    mockMvc
        .perform(
            post("/v1/time-capsules")
                .header(AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"title\":\"캡슐\",\"capsuleType\":\"GROUP\",\"visibilityType\":\"ALL_MEMBERS\",\"openAt\":\""
                        + openAt
                        + "\"}"))
        .andExpect(status().is(expectedStatus))
        .andExpect(jsonPath("$.status").value(expectedStatus));
  }

  private void cleanUp() {
    capsuleMemberRepository.deleteAllInBatch();
    timeCapsuleRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();
  }
}

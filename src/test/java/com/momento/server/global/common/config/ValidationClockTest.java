package com.momento.server.global.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Future;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 요청 검증의 {@code @Future} 가 시스템 시계가 아니라 {@link Clock} 빈을 기준으로 판단하는지 검증한다. 지금은 요청 DTO 에
 * {@code @Future} 를 쓰는 곳이 없어, 스프링이 만든 {@link Validator} 로 테스트 전용 값을 직접 검증한다.
 */
@SpringBootTest
class ValidationClockTest {

  private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

  @Autowired private Validator validator;

  @MockitoBean private Clock clock;

  @BeforeEach
  void setUp() {
    given(clock.instant()).willReturn(NOW);
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
  }

  @ParameterizedTest(name = "고정된 현재 시각 기준 {0}초 → 통과 {1}")
  @CsvSource({"1, true", "0, false", "-1, false"})
  @DisplayName("@Future 는 Clock 빈이 가리키는 현재 시각보다 뒤여야 통과한다")
  void futureIsJudgedByClockBean(long offsetSeconds, boolean valid) {
    LocalDateTime at = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).plusSeconds(offsetSeconds);

    assertThat(validator.validate(new Scheduled(at)).isEmpty()).isEqualTo(valid);
  }

  private record Scheduled(@Future LocalDateTime at) {}
}

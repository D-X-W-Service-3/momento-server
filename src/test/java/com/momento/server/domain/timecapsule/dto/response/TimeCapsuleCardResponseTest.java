package com.momento.server.domain.timecapsule.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class TimeCapsuleCardResponseTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 12, 12);

  @ParameterizedTest(name = "개봉 {0} → D-Day {1}")
  @CsvSource({"2026-12-20T00:00, 8", "2026-12-12T00:00, 0", "2026-11-12T00:00, -30"})
  @DisplayName("D-Day 는 오늘부터 개봉일까지의 날짜 차이다. 당일 0, 지나면 음수다")
  void dDayIsDateDifference(LocalDateTime openAt, int expected) {
    TimeCapsule capsule = capsule(CapsuleStatus.LOCKED, openAt.minusDays(5), openAt);

    assertThat(card(capsule).dDay()).isEqualTo(expected);
  }

  @Test
  @DisplayName("마감이 오늘 23:59:59 면 마감 D-Day 는 0 이다")
  void deadlineDDayCountsDatesNotHours() {
    TimeCapsule capsule =
        capsule(CapsuleStatus.WRITING, TODAY.atTime(23, 59, 59), TODAY.plusDays(2).atStartOfDay());

    assertThat(card(capsule).deadlineDDay()).isZero();
  }

  @Test
  @DisplayName("작성 중이면 마감까지 남은 날짜를 준다")
  void deadlineDDayForWriting() {
    TimeCapsule capsule =
        capsule(
            CapsuleStatus.WRITING,
            TODAY.plusDays(3).atTime(23, 59, 59),
            TODAY.plusDays(10).atStartOfDay());

    assertThat(card(capsule).deadlineDDay()).isEqualTo(3);
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(
      value = CapsuleStatus.class,
      names = {"LOCKED", "OPENED"})
  @DisplayName("작성 중이 아니면 마감 D-Day 는 null 이다")
  void noDeadlineDDayAfterWriting(CapsuleStatus status) {
    TimeCapsule capsule =
        capsule(status, TODAY.minusDays(3).atTime(23, 59, 59), TODAY.plusDays(1).atStartOfDay());

    assertThat(card(capsule).deadlineDDay()).isNull();
  }

  @Test
  @DisplayName("JSON 필드 이름은 명세대로 dDay · deadlineDDay 다")
  void serializesFieldNamesAsSpec() throws Exception {
    TimeCapsule capsule =
        capsule(CapsuleStatus.WRITING, TODAY.atTime(23, 59, 59), TODAY.plusDays(2).atStartOfDay());

    String json =
        new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(card(capsule));

    assertThat(json).contains("\"dDay\":2", "\"deadlineDDay\":0", "\"openedByMe\":false");
  }

  private TimeCapsuleCardResponse card(TimeCapsule capsule) {
    return TimeCapsuleCardResponse.of(capsule, false, 1, 0, TODAY);
  }

  private TimeCapsule capsule(
      CapsuleStatus status, LocalDateTime letterDeadlineAt, LocalDateTime openAt) {
    return TimeCapsule.builder()
        .iconType("ICE_CREAM")
        .title("캡슐")
        .status(status)
        .letterDeadlineAt(letterDeadlineAt)
        .openAt(openAt)
        .build();
  }
}

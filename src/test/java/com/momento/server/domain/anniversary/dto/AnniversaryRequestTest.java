package com.momento.server.domain.anniversary.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.momento.server.domain.anniversary.dto.request.AnniversaryRequest;
import com.momento.server.domain.anniversary.dto.response.AnniversaryDetailResponse;
import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.anniversary.entity.RepeatType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnniversaryRequestTest {

  private static final String REMIND_MESSAGE = "미리 알림은 1, 3, 5, 7, 10일 전 중에서 선택해야 합니다.";

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  @DisplayName("필수값만 보내면 통과한다")
  void requiredOnly() {
    assertThat(messages(request("우리 100일", null, null, null))).isEmpty();
  }

  @Test
  @DisplayName("제목 · 메모는 앞뒤 공백을 빼고, 공백뿐인 메모는 null 이다")
  void stripsStrings() {
    assertThat(request("  우리 100일  ", " 카페 가기 ", null, null).title()).isEqualTo("우리 100일");
    assertThat(request("우리 100일", " 카페 가기 ", null, null).memo()).isEqualTo("카페 가기");
    assertThat(request("우리 100일", "   ", null, null).memo()).isNull();
  }

  @Test
  @DisplayName("알림 시각은 분 단위로 자른다")
  void truncatesNotifyTime() {
    assertThat(request("우리 100일", null, LocalTime.of(9, 0, 30), null).notifyTime())
        .isEqualTo(LocalTime.of(9, 0));
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"", "   "})
  @DisplayName("제목이 비어 있으면 실패한다")
  void rejectsBlankTitle(String title) {
    assertThat(messages(request(title, null, null, null))).containsExactly("기념일 이름은 필수입니다.");
  }

  @Test
  @DisplayName("제목은 50자까지, 메모는 200자까지 받는다")
  void lengthBoundaries() {
    assertThat(messages(request("가".repeat(50), "가".repeat(200), null, null))).isEmpty();
    assertThat(messages(request("가".repeat(51), null, null, null)))
        .containsExactly("기념일 이름은 50자 이하여야 합니다.");
    assertThat(messages(request("우리 100일", "가".repeat(201), null, null)))
        .containsExactly("메모는 200자 이하여야 합니다.");
  }

  @Test
  @DisplayName("날짜 · 반복 유형 · 알림 받기 여부가 없으면 실패한다")
  void rejectsMissingRequired() {
    assertThat(messages(new AnniversaryRequest("우리 100일", null, null, null, null, null, null)))
        .containsExactlyInAnyOrder("기념일 날짜는 필수입니다.", "반복 유형은 필수입니다.", "알림 받기 여부는 필수입니다.");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(ints = {1, 3, 5, 7, 10})
  @DisplayName("미리 알림은 1 · 3 · 5 · 7 · 10 을 받는다")
  void acceptsRemindDays(int days) {
    assertThat(messages(request("우리 100일", null, null, days))).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(ints = {0, 2, 11, -1})
  @DisplayName("미리 알림이 정해진 값이 아니면 실패한다")
  void rejectsRemindDays(int days) {
    assertThat(messages(request("우리 100일", null, null, days))).containsExactly(REMIND_MESSAGE);
  }

  @Test
  @DisplayName("상세 응답의 dDay 는 다음 기념일까지 남은 일수다. 지난 반복 없는 기념일은 음수다")
  void detailDDay() {
    LocalDate today = LocalDate.of(2026, 10, 29);
    Anniversary yearly =
        Anniversary.create(
            null, "기념일", LocalDate.of(2025, 11, 1), RepeatType.YEARLY, null, false, null, null);
    Anniversary past =
        Anniversary.create(
            null, "기념일", LocalDate.of(2026, 10, 19), RepeatType.NONE, null, false, null, null);

    AnniversaryDetailResponse yearlyResponse = AnniversaryDetailResponse.of(yearly, today);

    assertThat(yearlyResponse.nextDate()).isEqualTo(LocalDate.of(2026, 11, 1));
    assertThat(yearlyResponse.dDay()).isEqualTo(3);
    assertThat(AnniversaryDetailResponse.of(past, today).dDay()).isEqualTo(-10);
  }

  private static AnniversaryRequest request(
      String title, String memo, LocalTime notifyTime, Integer remindDaysBefore) {
    return new AnniversaryRequest(
        title,
        LocalDate.of(2026, 8, 12),
        RepeatType.YEARLY,
        memo,
        true,
        notifyTime,
        remindDaysBefore);
  }

  private List<String> messages(AnniversaryRequest request) {
    return validator.validate(request).stream().map(ConstraintViolation::getMessage).toList();
  }
}

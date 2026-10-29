package com.momento.server.domain.timecapsule.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.momento.server.global.common.code.GlobalErrorCode;
import com.momento.server.global.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TimeCapsuleCursorTest {

  @Test
  @DisplayName("인코딩한 커서를 해석하면 같은 개봉일 · ID 가 나온다")
  void roundTrips() {
    TimeCapsuleCursor cursor = new TimeCapsuleCursor(LocalDateTime.of(2045, 1, 1, 0, 0), 25L);

    assertThat(TimeCapsuleCursor.parse(cursor.encode())).isEqualTo(cursor);
  }

  @Test
  @DisplayName("쿼리 파라미터에 넣을 때 인코딩해야 하는 글자가 없다")
  void isUrlSafe() {
    String encoded = new TimeCapsuleCursor(LocalDateTime.of(2045, 12, 31, 0, 0), 999L).encode();

    assertThat(encoded).matches("[A-Za-z0-9_-]+");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  @DisplayName("커서가 없으면 첫 페이지다")
  void blankMeansFirstPage(String cursor) {
    assertThat(TimeCapsuleCursor.parse(cursor)).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-base64!", "2045-01-01T00:00|25", "MjA0NQ"})
  @DisplayName("해석할 수 없는 커서는 400 이다")
  void rejectsBrokenCursor(String cursor) {
    assertThatThrownBy(() -> TimeCapsuleCursor.parse(cursor))
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2045-13-01T00:00|25", "2045-01-01T00:00|abc", "|25"})
  @DisplayName("형식은 맞지만 값이 깨진 커서도 400 이다")
  void rejectsBrokenValues(String raw) {
    String cursor =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));

    assertThatThrownBy(() -> TimeCapsuleCursor.parse(cursor))
        .isInstanceOf(ApiException.class)
        .extracting("errorCode")
        .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
  }
}

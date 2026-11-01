package com.momento.server.domain.timecapsule.facade;

import com.momento.server.global.common.code.GlobalErrorCode;
import com.momento.server.global.common.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 목록 다음 페이지의 시작 위치. 직전 페이지 마지막 캡슐의 (개봉일, ID) 다.
 *
 * <p>클라이언트에는 {@code "2045-01-01T00:00|25"} 를 URL-safe Base64 로 바꾼 문자열 하나로 내려준다. 클라이언트가 해석하지 않으므로
 * 형식을 바꿔도 API 계약은 그대로다. URL-safe 에 패딩을 빼서 쿼리 파라미터에 그대로 넣어도 인코딩할 글자가 없다.
 */
public record TimeCapsuleCursor(LocalDateTime openAt, Long id) {

  private static final String SEPARATOR = "|";

  public String encode() {
    String raw = openAt + SEPARATOR + id;
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * @return 커서가 없으면 null (첫 페이지)
   * @throws ApiException 해석할 수 없는 커서면 400
   */
  public static TimeCapsuleCursor parse(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      int separator = raw.lastIndexOf(SEPARATOR);
      if (separator < 0) {
        throw new ApiException(GlobalErrorCode.INVALID_INPUT_VALUE);
      }
      return new TimeCapsuleCursor(
          LocalDateTime.parse(raw.substring(0, separator)),
          Long.valueOf(raw.substring(separator + 1)));
    } catch (IllegalArgumentException | DateTimeParseException exception) {
      throw new ApiException(GlobalErrorCode.INVALID_INPUT_VALUE, exception);
    }
  }
}

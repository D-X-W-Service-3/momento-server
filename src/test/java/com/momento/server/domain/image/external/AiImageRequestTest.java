package com.momento.server.domain.image.external;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.momento.server.domain.image.external.dto.AiImageRequest;
import com.momento.server.domain.letter.entity.LetterThemeType;
import com.momento.server.domain.letter.entity.LetterType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AiImageRequestTest {

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({
    "BIRTHDAY, birthday",
    "CHRISTMAS, christmas",
    "COUPLE_ANNIVERSARY, anniversary",
    "GENERAL, basic",
    ", basic"
  })
  @DisplayName("편지 유형은 AI 서버의 기념일 값으로 바꾼다. 일반 · 유형 없음은 기본(basic)이다")
  void event(LetterType letterType, String expected) {
    assertThat(AiImageRequest.of(1L, "편지", letterType, LetterThemeType.PASTEL).event())
        .isEqualTo(expected);
  }

  @ParameterizedTest(name = "{0} → {1}")
  @CsvSource({
    "PASTEL, pstl",
    "WATERCOLOR, water",
    "VINTAGE, vintage",
    "ANIME, anime",
    "JOSEON, josen"
  })
  @DisplayName("편지지 화풍은 AI 서버의 화풍 값으로 바꾼다")
  void style(LetterThemeType themeType, String expected) {
    assertThat(AiImageRequest.of(1L, "편지", LetterType.BIRTHDAY, themeType).style())
        .isEqualTo(expected);
  }

  @Test
  @DisplayName("본문과 편지 ID 를 담고, 요청 ID 는 request_id 로 보낸다")
  void json() throws Exception {
    AiImageRequest request =
        AiImageRequest.of(31L, "생일 축하해!", LetterType.BIRTHDAY, LetterThemeType.PASTEL);

    assertThat(new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(request)))
        .isEqualTo(
            new ObjectMapper()
                .readTree(
                    "{\"letter\":\"생일 축하해!\",\"event\":\"birthday\",\"style\":\"pstl\","
                        + "\"request_id\":\"31\"}"));
  }
}

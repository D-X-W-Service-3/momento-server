package com.momento.server.global.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LikePatternTest {

  @Test
  @DisplayName("검색어가 비어 있거나 공백뿐이면 검색하지 않는다")
  void blankKeywordMeansNoSearch() {
    assertThat(LikePattern.contains(null)).isNull();
    assertThat(LikePattern.contains("  ")).isNull();
  }

  @Test
  @DisplayName("앞뒤 공백을 빼고 양쪽에 % 를 붙인다")
  void wrapsStrippedKeyword() {
    assertThat(LikePattern.contains(" 제주 ")).isEqualTo("%제주%");
  }

  @Test
  @DisplayName("% · _ · 이스케이프 문자 ! 는 글자 그대로 찾도록 이스케이프한다")
  void escapesWildcards() {
    assertThat(LikePattern.contains("100%")).isEqualTo("%100!%%");
    assertThat(LikePattern.contains("a_b")).isEqualTo("%a!_b%");
    assertThat(LikePattern.contains("와!")).isEqualTo("%와!!%");
  }
}

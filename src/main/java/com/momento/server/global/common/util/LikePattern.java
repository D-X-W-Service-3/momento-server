package com.momento.server.global.common.util;

/**
 * 검색어를 LIKE 패턴으로 바꾼다. 이스케이프 문자는 {@code !} 라, 이 패턴을 쓰는 쿼리는 {@code like :pattern escape '!'} 로 쓴다. 캡슐
 * 목록 · 통합 검색이 같은 규칙으로 찾도록 한곳에 둔다.
 */
public final class LikePattern {

  private LikePattern() {}

  /**
   * 검색어를 "포함" 검색용 LIKE 패턴으로 바꾼다. {@code %} · {@code _} 를 이스케이프하지 않으면 {@code %} 하나로 모든 행이 나온다. 비어
   * 있으면 null 이라 검색하지 않는다.
   */
  public static String contains(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    String escaped = keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_");
    return "%" + escaped + "%";
  }
}

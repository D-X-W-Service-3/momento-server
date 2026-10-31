package com.momento.server.domain.image.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.momento.server.domain.image.entity.ImageTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ImageKeysTest {

  private static final String MEMORY_KEY = "memories/1/3f2b6c1e-8a4d-4f1b-9c3e-2d7a5b6c8e9f.jpg";

  @ParameterizedTest(name = "[{index}] {0} · {1} → {2}/.{3}")
  @CsvSource({
    "MEMORY, image/jpeg, memories, jpg",
    "MEMORY, image/png, memories, png",
    "PROFILE, image/webp, profiles, webp",
    "PROFILE, image/heic, profiles, heic"
  })
  @DisplayName("새 key 는 용도 경로 · 회원 ID 아래 UUID 파일 이름에 형식별 확장자를 붙인다")
  void newKey(ImageTarget target, String contentType, String directory, String extension) {
    String key = ImageKeys.newKey(target, 7L, contentType);

    assertThat(key).startsWith(directory + "/7/").endsWith("." + extension);
    assertThat(ImageKeys.isOwnedBy(target, key, 7L)).isTrue();
  }

  @Test
  @DisplayName("같은 형식이어도 매번 다른 key 를 만든다")
  void newKeyIsUnique() {
    assertThat(ImageKeys.newKey(ImageTarget.MEMORY, 7L, "image/png"))
        .isNotEqualTo(ImageKeys.newKey(ImageTarget.MEMORY, 7L, "image/png"));
  }

  @Test
  @DisplayName("허용하지 않는 형식으로는 key 를 만들지 않는다")
  void unsupportedType() {
    assertThatThrownBy(() -> ImageKeys.newKey(ImageTarget.MEMORY, 7L, "image/gif"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("다른 회원 경로의 key 는 내 것이 아니다")
  void otherUsersKey() {
    assertThat(ImageKeys.isOwnedBy(ImageTarget.MEMORY, MEMORY_KEY, 1L)).isTrue();
    assertThat(ImageKeys.isOwnedBy(ImageTarget.MEMORY, MEMORY_KEY, 11L)).isFalse();
    assertThat(ImageKeys.isOwnedBy(ImageTarget.MEMORY, MEMORY_KEY, 2L)).isFalse();
  }

  @Test
  @DisplayName("다른 용도로 발급한 key 는 그 용도의 key 가 아니다")
  void otherTargetKey() {
    String profileKey = "profiles/1/3f2b6c1e-8a4d-4f1b-9c3e-2d7a5b6c8e9f.jpg";

    assertThat(ImageKeys.isOwnedBy(ImageTarget.MEMORY, profileKey, 1L)).isFalse();
    assertThat(ImageKeys.isOwnedBy(ImageTarget.PROFILE, profileKey, 1L)).isTrue();
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(
      strings = {
        "https://cdn.test/a.jpg",
        "memories/1/a.jpg",
        "memories/1/../2/3f2b6c1e-8a4d-4f1b-9c3e-2d7a5b6c8e9f.jpg",
        "memories/1/3f2b6c1e-8a4d-4f1b-9c3e-2d7a5b6c8e9f.gif",
        "/memories/1/3f2b6c1e-8a4d-4f1b-9c3e-2d7a5b6c8e9f.jpg"
      })
  @DisplayName("발급한 모양이 아닌 key 는 내 것으로 보지 않는다")
  void malformedKey(String key) {
    assertThat(ImageKeys.isOwnedBy(ImageTarget.MEMORY, key, 1L)).isFalse();
  }
}

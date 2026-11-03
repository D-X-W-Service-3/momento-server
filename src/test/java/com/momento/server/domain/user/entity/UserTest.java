package com.momento.server.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final String KAKAO_URL = "https://k.kakaocdn.net/profile.jpg";
  private static final String KEY = "profiles/1/3f2b6c1e-8a4d-4f1b-9c3e-2d7a5b6c8e9f.jpg";
  private static final Function<String, String> VIEW_URL_OF = key -> "https://s3/" + key;

  @Test
  @DisplayName("올린 사진이 없으면 카카오 사진 URL 이 보인다")
  void showsKakaoImageWithoutUpload() {
    User user = User.builder().profileImageUrl(KAKAO_URL).build();

    assertThat(user.profileImageUrl(VIEW_URL_OF)).isEqualTo(KAKAO_URL);
  }

  @Test
  @DisplayName("올린 사진이 있으면 카카오 사진 대신 그 조회 주소가 보인다")
  void showsUploadedImage() {
    User user = User.builder().profileImageUrl(KAKAO_URL).build();

    user.changeProfileImage(KEY);

    assertThat(user.profileImageUrl(VIEW_URL_OF)).isEqualTo("https://s3/" + KEY);
  }

  @Test
  @DisplayName("사진을 지우면 올린 사진과 카카오 사진이 모두 사라진다")
  void removesBothImages() {
    User user = User.builder().profileImageUrl(KAKAO_URL).build();
    user.changeProfileImage(KEY);

    user.removeProfileImage();

    assertThat(user.getProfileImageKey()).isNull();
    assertThat(user.getProfileImageUrl()).isNull();
    assertThat(user.profileImageUrl(VIEW_URL_OF)).isNull();
  }
}

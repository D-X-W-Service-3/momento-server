package com.momento.server.domain.auth.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

/**
 * 카카오 {@code GET /v2/user/me} 응답 중 회원 가입에 필요한 필드만 매핑한다.
 *
 * <p>닉네임/프로필 이미지는 사용자가 동의하지 않으면 내려오지 않으므로 모두 null 일 수 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

  public String nicknameOrNull() {
    return profile().map(Profile::nickname).orElse(null);
  }

  public String profileImageUrlOrNull() {
    return profile().map(Profile::profileImageUrl).orElse(null);
  }

  private Optional<Profile> profile() {
    return Optional.ofNullable(kakaoAccount).map(KakaoAccount::profile);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record KakaoAccount(Profile profile) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Profile(
      String nickname, @JsonProperty("profile_image_url") String profileImageUrl) {}
}

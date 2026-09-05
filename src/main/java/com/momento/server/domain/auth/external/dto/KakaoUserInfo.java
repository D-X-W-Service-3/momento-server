package com.momento.server.domain.auth.external.dto;

/**
 * 카카오 응답을 가입에 바로 쓸 수 있게 정규화한 값.
 *
 * <p>{@code users.nickname} 은 NOT NULL 이지만 카카오는 프로필 동의를 받지 못하면 닉네임을 주지 않으므로 기본 닉네임으로 채운다.
 */
public record KakaoUserInfo(String kakaoId, String nickname, String profileImageUrl) {

  private static final String DEFAULT_NICKNAME = "모멘토 친구";

  public static KakaoUserInfo from(KakaoUserResponse response) {
    String nickname = response.nicknameOrNull();

    return new KakaoUserInfo(
        String.valueOf(response.id()),
        (nickname == null || nickname.isBlank()) ? DEFAULT_NICKNAME : nickname,
        response.profileImageUrlOrNull());
  }
}

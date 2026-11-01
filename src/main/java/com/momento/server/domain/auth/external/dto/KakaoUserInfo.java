package com.momento.server.domain.auth.external.dto;

/**
 * 카카오 응답을 가입에 바로 쓸 수 있게 정규화한 값.
 *
 * <p>카카오 닉네임은 쓰지 않는다. 닉네임은 겹치면 안 되는데 카카오 닉네임은 겹칠 수 있고, 동의하지 않으면 오지도 않는다. 가입할 때는 서버가 임시 닉네임을 넣고,
 * 사용자가 프로필 설정에서 바꾼다.
 */
public record KakaoUserInfo(String kakaoId, String profileImageUrl) {

  public static KakaoUserInfo from(KakaoUserResponse response) {
    return new KakaoUserInfo(String.valueOf(response.id()), response.profileImageUrlOrNull());
  }
}

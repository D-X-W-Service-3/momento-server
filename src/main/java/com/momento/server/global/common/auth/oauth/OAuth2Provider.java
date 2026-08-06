package com.momento.server.global.common.auth.oauth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OAuth2Provider {
  UNKNOWN("unknown"),
  KAKAO("kakao");

  private final String registrationId;

  public static OAuth2Provider byRegistrationId(String registrationId) {
    for (OAuth2Provider provider : values()) {
      if (provider.registrationId.equals(registrationId)) {
        return provider;
      }
    }
    return UNKNOWN;
  }
}

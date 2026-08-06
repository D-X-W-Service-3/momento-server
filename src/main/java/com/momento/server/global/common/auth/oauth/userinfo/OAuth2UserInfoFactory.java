package com.momento.server.global.common.auth.oauth.userinfo;

import com.momento.server.global.common.auth.oauth.OAuth2Provider;
import com.momento.server.global.common.auth.oauth.OAuth2UserInfo;
import java.util.Map;

public class OAuth2UserInfoFactory {

  private OAuth2UserInfoFactory() {}

  public static OAuth2UserInfo create(String registrationId, Map<String, Object> attributes) {
    return switch (OAuth2Provider.byRegistrationId(registrationId)) {
      case KAKAO -> KakaoOAuth2UserInfo.from(attributes);
      default -> null;
    };
  }
}

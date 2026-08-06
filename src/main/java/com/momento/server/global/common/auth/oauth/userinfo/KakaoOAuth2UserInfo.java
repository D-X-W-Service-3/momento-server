package com.momento.server.global.common.auth.oauth.userinfo;

import com.momento.server.global.common.auth.oauth.OAuth2Provider;
import com.momento.server.global.common.auth.oauth.OAuth2UserInfo;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(staticName = "from")
public class KakaoOAuth2UserInfo implements OAuth2UserInfo {

  @Getter private final Map<String, Object> attributes;

  @SuppressWarnings("unchecked")
  private Map<String, Object> getKakaoAccount() {
    return (HashMap<String, Object>) attributes.get("kakao_account");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getProperties() {
    return (HashMap<String, Object>) attributes.get("properties");
  }

  @Override
  public String getEmail() {
    return (String) getKakaoAccount().getOrDefault("email", FALLBACK_EMAIL);
  }

  @Override
  public String getName() {
    return (String) getProperties().getOrDefault("nickname", FALLBACK_NAME);
  }

  @Override
  public OAuth2Provider getProvider() {
    return OAuth2Provider.KAKAO;
  }

  @Override
  public String getSocialId() {
    return attributes.get("id").toString();
  }
}

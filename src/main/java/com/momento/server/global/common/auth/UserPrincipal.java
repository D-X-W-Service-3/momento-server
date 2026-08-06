package com.momento.server.global.common.auth;

import com.momento.server.domain.user.entity.User;
import com.momento.server.global.common.auth.oauth.OAuth2Provider;
import com.momento.server.global.common.auth.oauth.OAuth2UserInfo;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class UserPrincipal implements UserDetails, OAuth2User {

  @Getter private final Long userId;

  private final String email;

  @Getter private final OAuth2UserInfo oAuth2UserInfo;

  @Getter private final Map<String, Object> attributes;

  public static UserPrincipal fromEntity(User user) {
    return new UserPrincipal(user.getId(), user.getEmail(), null, Map.of());
  }

  public static UserPrincipal fromOAuth2UserInfo(OAuth2UserInfo oAuth2UserInfo) {
    return new UserPrincipal(
        null, oAuth2UserInfo.getEmail(), oAuth2UserInfo, oAuth2UserInfo.getAttributes());
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_USER"));
  }

  @Override
  public String getPassword() {
    return "";
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String getName() {
    return Optional.ofNullable(oAuth2UserInfo).map(OAuth2UserInfo::getName).orElse("");
  }

  public OAuth2Provider getOAuth2Provider() {
    return Optional.ofNullable(oAuth2UserInfo)
        .map(OAuth2UserInfo::getProvider)
        .orElse(OAuth2Provider.UNKNOWN);
  }
}

package com.momento.server.global.common.auth.service;

import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.auth.oauth.OAuth2UserInfo;
import com.momento.server.global.common.auth.oauth.userinfo.OAuth2UserInfoFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
    OAuth2User user = super.loadUser(userRequest);
    String registrationId = userRequest.getClientRegistration().getRegistrationId();
    OAuth2UserInfo oAuth2UserInfo =
        OAuth2UserInfoFactory.create(registrationId, user.getAttributes());

    return UserPrincipal.fromOAuth2UserInfo(oAuth2UserInfo);
  }
}

package com.momento.server.global.common.auth.oauth.handler;

import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.auth.oauth.OAuth2Provider;
import com.momento.server.global.common.auth.oauth.OAuth2UserInfo;
import com.momento.server.global.common.auth.service.TokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

  private static final Duration TOKEN_EXPIRY = Duration.ofDays(30);

  private final TokenProvider tokenProvider;
  private final UserRepository userRepository;

  @Value("${service.oauth.redirect-uri}")
  private String oauthRedirectUri;

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
    OAuth2UserInfo oAuth2UserInfo = userPrincipal.getOAuth2UserInfo();

    OAuth2Provider provider = oAuth2UserInfo.getProvider();
    String socialId = oAuth2UserInfo.getSocialId();
    String email = oAuth2UserInfo.getEmail();

    User user =
        userRepository
            .findBySocialProviderAndSocialId(provider, socialId)
            .orElseGet(
                () ->
                    userRepository.save(
                        User.builder()
                            .email(email)
                            .socialProvider(provider)
                            .socialId(socialId)
                            .build()));

    String accessToken = tokenProvider.generateToken(user, TOKEN_EXPIRY);

    String targetUrl =
        UriComponentsBuilder.fromUriString(oauthRedirectUri)
            .queryParam("token", accessToken)
            .build()
            .toUriString();

    getRedirectStrategy().sendRedirect(request, response, targetUrl);
  }
}

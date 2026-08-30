package com.momento.server.domain.auth.facade;

import com.momento.server.domain.auth.dto.response.UserLoginResponse;
import com.momento.server.domain.auth.external.dto.KakaoUserInfo;
import com.momento.server.domain.auth.service.KakaoOAuthService;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.service.UserService;
import com.momento.server.global.common.auth.service.TokenProvider;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthFacade {

  private static final Duration ACCESS_TOKEN_EXPIRY = Duration.ofDays(30);

  private final KakaoOAuthService kakaoOAuthService;
  private final UserService userService;
  private final TokenProvider tokenProvider;

  /** 카카오 인가 코드로 회원을 조회·가입시키고 서비스 액세스 토큰을 발급한다. */
  public UserLoginResponse loginWithKakao(String authorizationCode) {
    KakaoUserInfo kakaoUser = kakaoOAuthService.getUserInfo(authorizationCode);
    Optional<User> found = userService.findByKakaoId(kakaoUser.kakaoId());

    User user =
        found
            .map(
                existing ->
                    userService.restoreIfWithdrawn(
                        existing.getId(), kakaoUser.nickname(), kakaoUser.profileImageUrl()))
            .orElseGet(
                () ->
                    userService.register(
                        kakaoUser.kakaoId(), kakaoUser.nickname(), kakaoUser.profileImageUrl()));

    String accessToken = tokenProvider.generateToken(user, ACCESS_TOKEN_EXPIRY);

    return UserLoginResponse.of(accessToken, found.isEmpty());
  }
}

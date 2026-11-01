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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthFacade {

  private static final Duration ACCESS_TOKEN_EXPIRY = Duration.ofDays(30);

  private final KakaoOAuthService kakaoOAuthService;
  private final UserService userService;
  private final TokenProvider tokenProvider;

  /** 카카오 인가 코드로 회원을 조회·가입시키고 서비스 액세스 토큰을 발급한다. */
  public UserLoginResponse loginWithKakao(String authorizationCode, String redirectUri) {
    KakaoUserInfo kakaoUser = kakaoOAuthService.getUserInfo(authorizationCode, redirectUri);
    Optional<User> found = userService.findByKakaoId(kakaoUser.kakaoId());

    User user =
        found
            .map(existing -> userService.restoreIfWithdrawn(existing.getId()))
            .orElseGet(() -> register(kakaoUser));

    String accessToken = tokenProvider.generateToken(user, ACCESS_TOKEN_EXPIRY);

    return UserLoginResponse.of(accessToken, found.isEmpty());
  }

  /**
   * 가입이 UNIQUE 에 걸리면 한 번 더 시도한다. 걸리는 경우는 둘이다. 동시에 가입한 사람과 임시 닉네임이 겹쳤거나, 같은 카카오 계정의 로그인이 동시에 들어와 다른
   * 요청이 먼저 가입시켰다. 그래서 다시 가입시키기 전에 그 카카오 계정이 생겼는지 먼저 본다.
   *
   * <p>다시 시도는 서비스 트랜잭션 밖인 여기서 한다. 실패한 트랜잭션의 영속성 컨텍스트는 다시 쓸 수 없다.
   */
  private User register(KakaoUserInfo kakaoUser) {
    try {
      return userService.register(kakaoUser.kakaoId(), kakaoUser.profileImageUrl());
    } catch (DataIntegrityViolationException exception) {
      return userService
          .findByKakaoId(kakaoUser.kakaoId())
          .orElseGet(() -> userService.register(kakaoUser.kakaoId(), kakaoUser.profileImageUrl()));
    }
  }
}

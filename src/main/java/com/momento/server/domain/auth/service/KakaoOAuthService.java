package com.momento.server.domain.auth.service;

import com.momento.server.domain.auth.exception.AuthErrorCode;
import com.momento.server.domain.auth.external.KakaoApiClient;
import com.momento.server.domain.auth.external.KakaoAuthClient;
import com.momento.server.domain.auth.external.dto.KakaoTokenResponse;
import com.momento.server.domain.auth.external.dto.KakaoUserInfo;
import com.momento.server.domain.auth.external.dto.KakaoUserResponse;
import com.momento.server.global.common.exception.ApiException;
import com.momento.server.global.common.property.KakaoProperties;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * 프론트가 카카오에서 받은 인가 코드로 사용자 정보를 조회한다.
 *
 * <p>카카오 JavaScript SDK v2 는 클라이언트에 액세스 토큰을 주지 않고 인가 코드만 주므로, 토큰 교환은 REST API 키를 가진 서버가 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuthService {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String GRANT_TYPE = "authorization_code";

  private final KakaoAuthClient kakaoAuthClient;
  private final KakaoApiClient kakaoApiClient;
  private final KakaoProperties kakaoProperties;

  public KakaoUserInfo getUserInfo(String authorizationCode) {
    String kakaoAccessToken = exchangeToken(authorizationCode);
    KakaoUserResponse response = requestUserInfo(kakaoAccessToken);

    if (response == null || response.id() == null) {
      throw new ApiException(AuthErrorCode.INVALID_KAKAO_TOKEN);
    }

    return KakaoUserInfo.from(response);
  }

  private String exchangeToken(String authorizationCode) {
    KakaoTokenResponse response;
    try {
      response = kakaoAuthClient.issueToken(tokenRequestForm(authorizationCode));
    } catch (FeignException.BadRequest | FeignException.Unauthorized exception) {
      throw new ApiException(AuthErrorCode.INVALID_KAKAO_CODE, exception);
    } catch (FeignException exception) {
      log.error("카카오 토큰 발급 실패: status={}", exception.status(), exception);
      throw new ApiException(AuthErrorCode.KAKAO_SERVER_ERROR, exception);
    }

    if (response == null || response.accessToken() == null) {
      throw new ApiException(AuthErrorCode.INVALID_KAKAO_CODE);
    }

    return response.accessToken();
  }

  private MultiValueMap<String, String> tokenRequestForm(String authorizationCode) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", GRANT_TYPE);
    form.add("client_id", kakaoProperties.getClientId());
    form.add("redirect_uri", kakaoProperties.getRedirectUri());
    form.add("code", authorizationCode);

    String clientSecret = kakaoProperties.getClientSecret();
    if (clientSecret != null && !clientSecret.isBlank()) {
      form.add("client_secret", clientSecret);
    }

    return form;
  }

  private KakaoUserResponse requestUserInfo(String kakaoAccessToken) {
    try {
      return kakaoApiClient.getUserInfo(BEARER_PREFIX + kakaoAccessToken);
    } catch (FeignException.Unauthorized | FeignException.Forbidden exception) {
      throw new ApiException(AuthErrorCode.INVALID_KAKAO_TOKEN, exception);
    } catch (FeignException exception) {
      log.error("카카오 사용자 정보 조회 실패: status={}", exception.status(), exception);
      throw new ApiException(AuthErrorCode.KAKAO_SERVER_ERROR, exception);
    }
  }
}

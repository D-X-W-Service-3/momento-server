package com.momento.server.domain.auth.service;

import com.momento.server.domain.auth.exception.AuthErrorCode;
import com.momento.server.domain.auth.external.KakaoApiClient;
import com.momento.server.domain.auth.external.dto.KakaoUserInfo;
import com.momento.server.domain.auth.external.dto.KakaoUserResponse;
import com.momento.server.global.common.exception.ApiException;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuthService {

  private static final String BEARER_PREFIX = "Bearer ";

  private final KakaoApiClient kakaoApiClient;

  public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
    KakaoUserResponse response = requestUserInfo(kakaoAccessToken);

    if (response == null || response.id() == null) {
      throw new ApiException(AuthErrorCode.INVALID_KAKAO_TOKEN);
    }

    return KakaoUserInfo.from(response);
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

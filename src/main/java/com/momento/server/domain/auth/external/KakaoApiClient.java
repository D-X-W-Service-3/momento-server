package com.momento.server.domain.auth.external;

import com.momento.server.domain.auth.external.dto.KakaoUserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/** 카카오 API 서버. 프론트에서 받은 카카오 액세스 토큰으로 사용자 정보를 조회한다. */
@FeignClient(name = "kakaoApiClient", url = "${external.api-url.kakao}")
public interface KakaoApiClient {

  @GetMapping("/v2/user/me")
  KakaoUserResponse getUserInfo(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken);
}

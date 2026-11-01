package com.momento.server.domain.auth.external;

import com.momento.server.domain.auth.external.dto.KakaoTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** 카카오 인증 서버. 프론트가 받은 인가 코드를 액세스 토큰으로 교환한다. */
@FeignClient(name = "kakaoAuthClient", url = "${external.api-url.kakao-auth}")
public interface KakaoAuthClient {

  @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  KakaoTokenResponse issueToken(@RequestBody MultiValueMap<String, String> form);
}

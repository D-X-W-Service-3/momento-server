package com.momento.server.domain.image.external;

import com.momento.server.domain.image.external.dto.AiImageRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 별도 AI 서버의 이미지 생성 API. 편지 한 통으로 PNG(1024×1024) 한 장을 만들어 본문으로 돌려준다.
 *
 * <p>한 장에 약 2분 30초가 걸리고 GPU 가 1장이라 요청을 하나씩 처리한다. 그래서 읽기 타임아웃을 6분으로 길게 둔다({@code
 * spring.cloud.openfeign.client.config.aiImageClient}). 입력 오류는 400, 생성 실패는 500 이다.
 */
@FeignClient(name = "aiImageClient", url = "${external.api-url.ai}")
public interface AiImageClient {

  @PostMapping(
      value = "/generate",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.IMAGE_PNG_VALUE)
  byte[] generate(@RequestBody AiImageRequest request);
}

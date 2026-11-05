package com.momento.server.domain.push.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.momento.server.domain.push.entity.PushEndpoint;
import com.momento.server.domain.push.entity.PushSubscription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "웹 푸시 구독 등록 요청. 브라우저의 PushSubscription.toJSON() 결과를 그대로 보낸다.")
public record PushSubscriptionRequest(
    @Schema(
            description = "푸시 서비스 주소. 브라우저 푸시 서비스(FCM · Mozilla · Apple · WNS)의 https 주소만 받는다.",
            example = "https://fcm.googleapis.com/fcm/send/dXJ8...",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "endpoint 는 필수입니다.")
        @Size(
            max = PushSubscription.ENDPOINT_MAX_LENGTH,
            message = "endpoint 는 " + PushSubscription.ENDPOINT_MAX_LENGTH + "자 이하여야 합니다.")
        String endpoint,
    @Schema(
            description = "구독 만료 시각(epoch ms). 대부분의 브라우저가 null 을 보낸다. 서버는 저장하지 않는다.",
            nullable = true)
        Long expirationTime,
    @Schema(description = "페이로드 암호화 키", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "keys 는 필수입니다.")
        @Valid
        Keys keys) {

  @JsonIgnore
  @Schema(hidden = true)
  @AssertTrue(message = "지원하지 않는 푸시 서비스 주소입니다.")
  public boolean isEndpointAllowed() {
    return endpoint == null || endpoint.isBlank() || PushEndpoint.isAllowed(endpoint);
  }

  @Schema(name = "PushSubscriptionKeys", description = "페이로드 암호화 키")
  public record Keys(
      @Schema(description = "브라우저 공개키(base64url)", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "keys.p256dh 는 필수입니다.")
          @Size(max = 255, message = "keys.p256dh 는 255자 이하여야 합니다.")
          String p256dh,
      @Schema(description = "인증 비밀값(base64url)", requiredMode = Schema.RequiredMode.REQUIRED)
          @NotBlank(message = "keys.auth 는 필수입니다.")
          @Size(max = 255, message = "keys.auth 는 255자 이하여야 합니다.")
          String auth) {}
}

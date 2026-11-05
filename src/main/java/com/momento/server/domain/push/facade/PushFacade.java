package com.momento.server.domain.push.facade;

import com.momento.server.domain.push.dto.request.PushSubscriptionRequest;
import com.momento.server.domain.push.dto.response.VapidPublicKeyResponse;
import com.momento.server.domain.push.service.PushSubscriptionService;
import com.momento.server.global.common.property.VapidProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PushFacade {

  private final PushSubscriptionService pushSubscriptionService;
  private final VapidProperties vapidProperties;

  public VapidPublicKeyResponse getVapidPublicKey() {
    return new VapidPublicKeyResponse(vapidProperties.getPublicKey());
  }

  public void subscribe(Long userId, PushSubscriptionRequest request) {
    pushSubscriptionService.subscribe(
        userId, request.endpoint(), request.keys().p256dh(), request.keys().auth());
  }

  public void unsubscribe(Long userId, String endpoint) {
    pushSubscriptionService.unsubscribe(userId, endpoint);
  }
}

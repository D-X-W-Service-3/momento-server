package com.momento.server.domain.push.service;

import com.momento.server.domain.push.entity.PushSubscription;
import com.momento.server.domain.push.repository.PushSubscriptionRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushSubscriptionService {

  private final PushSubscriptionRepository pushSubscriptionRepository;
  private final UserRepository userRepository;

  /**
   * 구독을 등록한다. 같은 endpoint 가 이미 있으면 새 행을 만들지 않고 소유자와 키를 바꾼다. 같은 기기에서 다른 계정으로 로그인한 경우 이전 회원의 알림이 그
   * 기기에 계속 뜨지 않게 하기 위해서다.
   */
  @Transactional
  public void subscribe(Long userId, String endpoint, String p256dh, String auth) {
    User user = userRepository.getReferenceById(userId);
    pushSubscriptionRepository
        .findByEndpoint(endpoint)
        .ifPresentOrElse(
            subscription -> subscription.renew(user, p256dh, auth),
            () ->
                pushSubscriptionRepository.save(
                    PushSubscription.builder()
                        .user(user)
                        .endpoint(endpoint)
                        .p256dh(p256dh)
                        .auth(auth)
                        .build()));
  }

  /** 내 구독이면 지운다. 없거나 남의 구독이면 아무것도 하지 않는다. 로그아웃 중 재시도해도 실패하지 않게 하기 위해서다. */
  @Transactional
  public void unsubscribe(Long userId, String endpoint) {
    pushSubscriptionRepository.deleteByEndpointAndUserId(endpoint, userId);
  }

  @Transactional
  public void unsubscribeAll(Long userId) {
    pushSubscriptionRepository.deleteAllByUserId(userId);
  }
}

package com.momento.server.domain.notification.service;

import com.momento.server.domain.notification.entity.NotificationDraft;
import com.momento.server.domain.notification.repository.NotificationRecipientRepository;
import com.momento.server.domain.timecapsule.service.CapsuleMemberJoinedEvent;
import com.momento.server.domain.timecapsule.service.TimeCapsuleOpenedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 상태가 바뀌는 사건에 맞춰 알림을 보낸다.
 *
 * <p>사건을 일으킨 트랜잭션이 커밋된 뒤(AFTER_COMMIT)에 받는다. 알림 저장이 실패해도 캡슐 공개 · 참여는 이미 끝나 있고, 실패는 로그로만 남는다(Spring
 * 이 커밋 이후 콜백의 예외를 삼킨다). 커밋이 끝난 트랜잭션에는 더 쓸 수 없어 새 트랜잭션(REQUIRES_NEW)에서 저장한다.
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

  private final NotificationRecipientRepository recipientRepository;
  private final NotificationSender notificationSender;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onCapsuleOpened(TimeCapsuleOpenedEvent event) {
    notificationSender.send(
        recipientRepository.findCapsuleOpenedRecipients(event.capsuleIds()).stream()
            .map(
                recipient ->
                    NotificationDraft.capsuleOpened(recipient.userId(), recipient.capsuleId()))
            .toList());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onMemberJoined(CapsuleMemberJoinedEvent event) {
    notificationSender.send(
        recipientRepository.findNewMemberRecipients(event.capsuleId(), event.userId()).stream()
            .map(
                recipient ->
                    NotificationDraft.newMember(
                        recipient.userId(),
                        recipient.capsuleId(),
                        recipient.capsuleTitle(),
                        event.nickname()))
            .toList());
  }
}

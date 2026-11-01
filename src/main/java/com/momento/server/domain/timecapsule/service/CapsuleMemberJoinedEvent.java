package com.momento.server.domain.timecapsule.service;

/**
 * 초대를 수락해 회원이 캡슐에 들어왔을 때 발행한다. 나갔다가 다시 들어온 경우도 포함한다. 새 참여자 알림이 여기에 붙는다.
 *
 * <p>받을 때는 {@code @TransactionalEventListener(AFTER_COMMIT)} 을 쓴다({@link TimeCapsuleOpenedEvent} 와
 * 같은 이유). 알림 저장이 실패해도 참여가 롤백되면 안 된다.
 *
 * @param nickname 들어온 회원의 닉네임. 알림 문구에 쓴다
 */
public record CapsuleMemberJoinedEvent(Long capsuleId, Long userId, String nickname) {}

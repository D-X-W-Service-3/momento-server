package com.momento.server.domain.timecapsule.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 캡슐이 열렸을 때 발행한다. 공개 알림 발송이 여기에 붙는다.
 *
 * <p>지금은 받는 곳이 없고, 리스너가 없으면 발행은 아무 일도 하지 않는다. 알림을 붙일 때 리스너를 하나 더하면 되고 전이 쪽은 고치지 않는다. 상태를 저장한 뒤에
 * 무언가를 할 자리가 필요해서 스케줄러 방식을 골랐으므로, 그 자리를 비워 두는 것까지가 이번 작업이다.
 *
 * <p><b>받을 때는 {@code @TransactionalEventListener(AFTER_COMMIT)} 을 쓴다.</b> 이 이벤트는 상태를 바꾸는 트랜잭션 안에서
 * 발행되므로, 그냥 {@code @EventListener} 로 받으면 같은 트랜잭션에서 동기로 돌고 알림 저장이 실패할 때 상태 전이까지 함께 롤백된다. 알림을 못 보내는
 * 것과 캡슐이 열리지 않는 것은 심각도가 다르다.
 */
public record TimeCapsuleOpenedEvent(List<Long> capsuleIds, LocalDateTime openedAt) {}

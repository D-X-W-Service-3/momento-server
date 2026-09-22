package com.momento.server.domain.timecapsule.scheduler;

import com.momento.server.domain.timecapsule.service.TimeCapsuleStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 상태 전이를 주기적으로 돌린다. 실행 트리거만 맡고 전이 규칙은 서비스에 둔다 — 테스트는 서비스를 직접 부른다. */
@Component
@RequiredArgsConstructor
public class TimeCapsuleStatusScheduler {

  private final TimeCapsuleStatusService timeCapsuleStatusService;

  /**
   * 주기를 짧게 둔다. 공개 시각 정각에 들어온 사용자가 겪는 지연이 이 주기만큼이기 때문이다. 공개 알림을 타고 들어오는 사용자는 알림이 나가는 시점이 곧 상태가 바뀌는
   * 시점이라 지연을 겪지 않는다.
   *
   * <p>대상 캡슐이 없으면 {@code (status, open_at)} 인덱스만 타고 끝나 비용이 거의 없다.
   */
  @Scheduled(fixedDelayString = "${momento.capsule.status-transition-interval:10s}")
  public void transition() {
    timeCapsuleStatusService.transition();
  }
}

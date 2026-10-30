package com.momento.server.domain.notification.scheduler;

import com.momento.server.domain.notification.service.NotificationDispatchService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 날짜가 기준인 알림을 주기적으로 보낸다. 실행 트리거와 "어디까지 보냈는지"만 맡고 발송 규칙은 서비스에 둔다 — 테스트는 서비스를 직접 부른다.
 *
 * <p>지난번에 보낸 시각부터 지금까지를 한 구간으로 넘긴다. 정각에 한 번 도는 cron 이 아니라 구간으로 넘기는 이유는, 기념일 알림 시각이 분 단위로 제각각이고 주기가
 * 조금 밀려도 그 사이 시각을 빠뜨리지 않기 위해서다.
 *
 * <p>서버가 뜨면 그날 0시부터 넘긴다. 재시작 전에 이미 보낸 알림은 중복 방지 키가 건너뛴다. 하루 넘게 멈춰 있었다면 그 사이 날짜는 보내지 않는다 — 어제의 D-Day
 * 알림을 오늘 보내면 틀린 정보다.
 */
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

  private final NotificationDispatchService dispatchService;
  private final Clock clock;

  /** 다음 구간의 시작. 발송이 실패하면 넘기지 않아 다음 주기에 같은 구간을 다시 돈다. */
  private LocalDateTime dispatchedUntil;

  @Scheduled(fixedDelayString = "${momento.notification.dispatch-interval:1m}")
  public void dispatch() {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDateTime from =
        dispatchedUntil != null ? dispatchedUntil : now.toLocalDate().atStartOfDay();
    dispatchService.dispatch(from, now);
    dispatchedUntil = now;
  }
}

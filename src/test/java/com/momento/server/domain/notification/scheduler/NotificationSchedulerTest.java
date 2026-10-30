package com.momento.server.domain.notification.scheduler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.momento.server.domain.notification.service.NotificationDispatchService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationSchedulerTest {

  private static final LocalDateTime BOOT = LocalDateTime.of(2045, 3, 10, 10, 0);

  private final NotificationDispatchService dispatchService =
      mock(NotificationDispatchService.class);
  private final Clock clock = mock(Clock.class);
  private NotificationScheduler scheduler;

  @BeforeEach
  void setUp() {
    given(clock.getZone()).willReturn(ZoneOffset.UTC);
    scheduler = new NotificationScheduler(dispatchService, clock);
  }

  @Test
  @DisplayName("처음 돌 때는 그날 0시부터, 그다음은 지난번 시각부터 넘긴다")
  void advancesWindow() {
    at(BOOT);
    scheduler.dispatch();
    verify(dispatchService).dispatch(BOOT.toLocalDate().atStartOfDay(), BOOT);

    at(BOOT.plusMinutes(1));
    scheduler.dispatch();
    verify(dispatchService).dispatch(BOOT, BOOT.plusMinutes(1));
  }

  @Test
  @DisplayName("발송이 실패하면 구간을 넘기지 않아 다음 주기에 같은 시작부터 다시 돈다")
  void retriesFailedWindow() {
    at(BOOT);
    scheduler.dispatch();

    willThrow(new IllegalStateException("실패")).given(dispatchService).dispatch(any(), any());
    at(BOOT.plusMinutes(1));
    assertThatThrownBy(scheduler::dispatch).isInstanceOf(IllegalStateException.class);

    at(BOOT.plusMinutes(2));
    assertThatThrownBy(scheduler::dispatch).isInstanceOf(IllegalStateException.class);
    verify(dispatchService).dispatch(BOOT, BOOT.plusMinutes(2));
  }

  private void at(LocalDateTime now) {
    given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC));
  }
}

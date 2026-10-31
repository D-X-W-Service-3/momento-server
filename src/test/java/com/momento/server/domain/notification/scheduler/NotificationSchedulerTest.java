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

  @Test
  @DisplayName("주기가 자정을 넘으면 어제의 마지막 구간부터 이어서 넘긴다")
  void carriesOverYesterdayTailAcrossMidnight() {
    LocalDateTime beforeMidnight = LocalDateTime.of(2045, 3, 10, 23, 59, 30);
    LocalDateTime afterMidnight = beforeMidnight.plusMinutes(1);
    at(beforeMidnight);
    scheduler.dispatch();

    at(afterMidnight);
    scheduler.dispatch();
    verify(dispatchService).dispatch(beforeMidnight, afterMidnight);
  }

  @Test
  @DisplayName("발송이 실패한 채 자정을 한참 넘기면 어제 몫은 버리고 오늘 0시부터 넘긴다")
  void dropsYesterdayAfterLongFailure() {
    at(BOOT);
    scheduler.dispatch();

    willThrow(new IllegalStateException("실패")).given(dispatchService).dispatch(any(), any());
    LocalDateTime nextDay = BOOT.plusDays(1).withHour(3);
    at(nextDay);
    assertThatThrownBy(scheduler::dispatch).isInstanceOf(IllegalStateException.class);
    verify(dispatchService).dispatch(nextDay.toLocalDate().atStartOfDay(), nextDay);
  }

  @Test
  @DisplayName("같은 날 안에서는 실패가 오래 이어져도 실패한 시각부터 다시 넘긴다")
  void retriesWithinSameDay() {
    at(BOOT);
    scheduler.dispatch();

    willThrow(new IllegalStateException("실패")).given(dispatchService).dispatch(any(), any());
    LocalDateTime later = BOOT.plusHours(5);
    at(later);
    assertThatThrownBy(scheduler::dispatch).isInstanceOf(IllegalStateException.class);
    verify(dispatchService).dispatch(BOOT, later);
  }

  private void at(LocalDateTime now) {
    given(clock.instant()).willReturn(now.toInstant(ZoneOffset.UTC));
  }
}

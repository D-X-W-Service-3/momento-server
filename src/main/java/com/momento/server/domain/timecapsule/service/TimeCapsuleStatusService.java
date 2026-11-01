package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시각이 지난 타임캡슐의 상태를 옮긴다. 실행 주기는 스케줄러가 정하고, 여기에는 전이 규칙만 둔다. */
@Service
@RequiredArgsConstructor
public class TimeCapsuleStatusService {

  private final TimeCapsuleRepository timeCapsuleRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * 공개를 먼저 처리한다. 편지 마감과 공개 시각이 모두 지난 캡슐은 LOCKED 를 거칠 이유가 없는데, 잠그기를 먼저 하면 같은 주기에 WRITING → LOCKED →
   * OPENED 로 두 번 바뀐다.
   *
   * <p>현재 시각은 {@link Clock} 빈에서 한 번만 읽어 두 전이가 같은 시각을 본다.
   */
  @Transactional
  public void transition() {
    LocalDateTime now = LocalDateTime.now(clock);

    openCapsules(now);
    timeCapsuleRepository.lockAllPastDeadline(now);
  }

  /**
   * 상태를 바꾸기 전에 대상 ID 를 뽑는다. UPDATE 만 하면 어느 캡슐이 열렸는지 알 수 없어 알림을 보낼 수 없다.
   *
   * <p>실제로 바뀐 행이 없으면 이벤트를 내지 않는다. 서버가 한 대인 동안에는 뽑은 개수와 바뀐 개수가 항상 같지만, 여러 대로 늘리면 다른 인스턴스가 먼저 연 캡슐이
   * 섞여 알림이 중복될 수 있다. 그때는 캡슐마다 조건부 UPDATE 를 돌려 실제로 바꾼 것만 모아야 한다.
   */
  private void openCapsules(LocalDateTime now) {
    List<Long> capsuleIds = timeCapsuleRepository.findIdsToOpen(now);

    if (capsuleIds.isEmpty()) {
      return;
    }

    int opened = timeCapsuleRepository.openAll(capsuleIds);

    if (opened == 0) {
      return;
    }

    eventPublisher.publishEvent(new TimeCapsuleOpenedEvent(capsuleIds, now));
  }
}

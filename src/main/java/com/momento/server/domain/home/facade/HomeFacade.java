package com.momento.server.domain.home.facade;

import com.momento.server.domain.home.dto.response.HomeResponse;
import com.momento.server.domain.home.entity.VisitHistory;
import com.momento.server.domain.home.service.UserVisitService;
import com.momento.server.domain.notification.service.NotificationService;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleCardResponse;
import com.momento.server.domain.timecapsule.service.TimeCapsuleListService;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.service.UserService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 홈 화면을 사용자 · 알림 · 캡슐 · 방문 기록으로 모은다. 서비스를 따로 부르고 트랜잭션으로 묶지 않는다. 방문 기록이 먼저 커밋된 뒤 다른 조회가 실패해도, 다시 부르면
 * 같은 날이라 결과가 같다.
 */
@Component
@RequiredArgsConstructor
public class HomeFacade {

  private final UserService userService;
  private final NotificationService notificationService;
  private final TimeCapsuleListService timeCapsuleListService;
  private final UserVisitService userVisitService;
  private final Clock clock;

  /** 오늘을 한 번만 읽어 방문 기록과 D-Day 에 같이 쓴다. 홈을 조회하면 오늘이 방문으로 남는다. */
  public HomeResponse getHome(Long userId) {
    LocalDateTime now = LocalDateTime.now(clock);
    LocalDate today = now.toLocalDate();
    User user = userService.getActiveUser(userId);
    VisitHistory visits = userVisitService.visit(userId, now);

    List<TimeCapsuleCardResponse> capsules =
        timeCapsuleListService.getHomePreview(userId).stream()
            .map(
                summary ->
                    TimeCapsuleCardResponse.of(
                        summary.capsule(),
                        summary.openedByMe(),
                        summary.memberCount(),
                        summary.letterCount(),
                        today))
            .toList();

    return new HomeResponse(
        user.getNickname(),
        notificationService.countUnread(userId) > 0,
        timeCapsuleListService
            .getNextCapsule(userId)
            .map(capsule -> HomeResponse.UpcomingCapsule.of(capsule, today))
            .orElse(null),
        capsules,
        HomeResponse.WeeklyVisit.of(visits, today));
  }
}

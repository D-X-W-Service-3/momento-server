package com.momento.server.domain.timecapsule.facade;

import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleUpdateRequest;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleCardResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleCreateResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleDetailResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleListResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleOpenResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleOverviewResponse;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.service.TimeCapsuleDetail;
import com.momento.server.domain.timecapsule.service.TimeCapsuleListService;
import com.momento.server.domain.timecapsule.service.TimeCapsuleOverview;
import com.momento.server.domain.timecapsule.service.TimeCapsulePage;
import com.momento.server.domain.timecapsule.service.TimeCapsuleService;
import com.momento.server.domain.timecapsule.service.TimeCapsuleSummary;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.service.UserService;
import com.momento.server.global.common.code.GlobalErrorCode;
import com.momento.server.global.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimeCapsuleFacade {

  private static final int MAX_PAGE_SIZE = 100;

  private final UserService userService;
  private final TimeCapsuleService timeCapsuleService;
  private final TimeCapsuleListService timeCapsuleListService;
  private final Clock clock;

  public TimeCapsuleCreateResponse create(Long userId, TimeCapsuleCreateRequest request) {
    User creator = userService.getActiveUser(userId);

    return TimeCapsuleCreateResponse.from(timeCapsuleService.create(creator, request));
  }

  public void update(Long capsuleId, Long userId, TimeCapsuleUpdateRequest request) {
    timeCapsuleService.update(capsuleId, userId, request);
  }

  public void delete(Long capsuleId, Long userId) {
    timeCapsuleService.delete(capsuleId, userId);
  }

  public TimeCapsuleOpenResponse open(Long capsuleId, Long userId) {
    return new TimeCapsuleOpenResponse(capsuleId, timeCapsuleService.open(capsuleId, userId));
  }

  public TimeCapsuleDetailResponse getDetail(Long capsuleId, Long userId) {
    TimeCapsuleDetail detail = timeCapsuleService.getDetail(capsuleId, userId);

    return TimeCapsuleDetailResponse.of(detail, LocalDate.now(clock));
  }

  /**
   * {@code size} 는 {@code @Min} · {@code @Max} 대신 여기서 확인한다. {@code @RequestParam} 에 제약을 걸면 처리기가 없는
   * {@code HandlerMethodValidationException} 이 나서 500 이 된다.
   */
  public TimeCapsuleListResponse getList(
      Long userId, CapsuleStatus status, String keyword, String cursor, int size) {
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new ApiException(GlobalErrorCode.INVALID_INPUT_VALUE);
    }
    TimeCapsuleCursor after = TimeCapsuleCursor.parse(cursor);
    TimeCapsulePage page =
        timeCapsuleListService.getMyCapsules(
            userId,
            status,
            keyword,
            after == null ? null : after.openAt(),
            after == null ? null : after.id(),
            size);

    return new TimeCapsuleListResponse(
        TimeCapsuleListResponse.Counts.of(page.countsByStatus()),
        toCards(page.capsules(), LocalDate.now(clock)),
        page.hasNext() ? nextCursor(page.capsules()) : null,
        page.hasNext());
  }

  /** 네 섹션의 D-Day 를 같은 날짜로 계산하려고 오늘을 한 번만 읽는다. */
  public TimeCapsuleOverviewResponse getOverview(Long userId) {
    TimeCapsuleOverview overview = timeCapsuleListService.getOverview(userId);
    LocalDate today = LocalDate.now(clock);

    return new TimeCapsuleOverviewResponse(
        overview.hasPendingInvite(),
        toCards(overview.upcoming(), today),
        toCards(overview.writing(), today),
        toCards(overview.locked(), today),
        toCards(overview.opened(), today));
  }

  private String nextCursor(List<TimeCapsuleSummary> capsules) {
    TimeCapsule last = capsules.get(capsules.size() - 1).capsule();
    return new TimeCapsuleCursor(last.getOpenAt(), last.getId()).encode();
  }

  private List<TimeCapsuleCardResponse> toCards(
      List<TimeCapsuleSummary> capsules, LocalDate today) {
    return capsules.stream()
        .map(
            summary ->
                TimeCapsuleCardResponse.of(
                    summary.capsule(),
                    summary.openedByMe(),
                    summary.memberCount(),
                    summary.letterCount(),
                    today))
        .toList();
  }
}

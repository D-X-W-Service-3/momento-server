package com.momento.server.domain.timecapsule.facade;

import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleCreateResponse;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleDetailResponse;
import com.momento.server.domain.timecapsule.service.TimeCapsuleDetail;
import com.momento.server.domain.timecapsule.service.TimeCapsuleService;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimeCapsuleFacade {

  private final UserService userService;
  private final TimeCapsuleService timeCapsuleService;

  public TimeCapsuleCreateResponse create(Long userId, TimeCapsuleCreateRequest request) {
    User creator = userService.getActiveUser(userId);

    return TimeCapsuleCreateResponse.from(timeCapsuleService.create(creator, request));
  }

  public TimeCapsuleDetailResponse getDetail(Long capsuleId, Long userId) {
    TimeCapsuleDetail detail = timeCapsuleService.getDetail(capsuleId, userId);

    return TimeCapsuleDetailResponse.of(
        detail.capsule(), detail.myRole(), detail.memberCount(), detail.letterCount());
  }
}

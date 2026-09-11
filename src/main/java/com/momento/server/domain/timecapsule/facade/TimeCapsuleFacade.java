package com.momento.server.domain.timecapsule.facade;

import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleResponse;
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

  public TimeCapsuleResponse create(Long userId, TimeCapsuleCreateRequest request) {
    User creator = userService.getActiveUser(userId);

    return TimeCapsuleResponse.from(timeCapsuleService.create(creator, request));
  }
}

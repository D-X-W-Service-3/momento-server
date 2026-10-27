package com.momento.server.domain.user.facade;

import com.momento.server.domain.timecapsule.service.TimeCapsuleListService;
import com.momento.server.domain.user.dto.response.MyProfileResponse;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserFacade {

  private final UserService userService;
  private final TimeCapsuleListService timeCapsuleListService;

  /** 활동 요약은 저장하지 않고 조회할 때 센다. 캡슐 기준은 타임캡슐 목록 조회의 탭별 개수와 같다. */
  public MyProfileResponse getMyProfile(Long userId) {
    User user = userService.getActiveUser(userId);

    return MyProfileResponse.of(user, timeCapsuleListService.countMyCapsulesByStatus(userId));
  }
}

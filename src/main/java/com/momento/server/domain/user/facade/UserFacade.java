package com.momento.server.domain.user.facade;

import com.momento.server.domain.timecapsule.service.TimeCapsuleListService;
import com.momento.server.domain.user.dto.request.UserUpdateRequest;
import com.momento.server.domain.user.dto.response.MyProfileResponse;
import com.momento.server.domain.user.dto.response.NicknameAvailabilityResponse;
import com.momento.server.domain.user.dto.response.NotificationSettingsResponse;
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

  /** 수정 후 조회와 같은 형태를 돌려준다. 프로필 설정 화면이 저장 뒤 다시 조회하지 않아도 되게 한다. */
  public MyProfileResponse updateMyProfile(Long userId, UserUpdateRequest request) {
    User user = userService.updateProfile(userId, request);

    return MyProfileResponse.of(user, timeCapsuleListService.countMyCapsulesByStatus(userId));
  }

  public NicknameAvailabilityResponse checkNickname(Long userId, String nickname) {
    return new NicknameAvailabilityResponse(
        nickname, userService.isNicknameAvailable(userId, nickname));
  }

  public NotificationSettingsResponse getNotificationSettings(Long userId) {
    return NotificationSettingsResponse.from(
        userService.getActiveUser(userId).getNotificationSettings());
  }
}

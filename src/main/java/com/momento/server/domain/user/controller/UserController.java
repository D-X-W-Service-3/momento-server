package com.momento.server.domain.user.controller;

import com.momento.server.domain.user.dto.request.NicknameAvailabilityRequest;
import com.momento.server.domain.user.dto.request.NotificationSettingsUpdateRequest;
import com.momento.server.domain.user.dto.request.UserUpdateRequest;
import com.momento.server.domain.user.dto.response.MyProfileResponse;
import com.momento.server.domain.user.dto.response.NicknameAvailabilityResponse;
import com.momento.server.domain.user.dto.response.NotificationSettingsResponse;
import com.momento.server.domain.user.facade.UserFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestApiController("/v1/users")
@RequiredArgsConstructor
public class UserController implements UserApi {

  private final UserFacade userFacade;

  @Override
  @GetMapping("/me")
  public CommonResponse<MyProfileResponse> getMyProfile(
      @AuthenticationPrincipal UserPrincipal principal) {
    return CommonResponse.ok(userFacade.getMyProfile(principal.getUserId()));
  }

  @Override
  @PatchMapping("/me")
  public CommonResponse<MyProfileResponse> updateMyProfile(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody UserUpdateRequest request) {
    return CommonResponse.ok(userFacade.updateMyProfile(principal.getUserId(), request));
  }

  @Override
  @GetMapping("/nickname/availability")
  public CommonResponse<NicknameAvailabilityResponse> checkNickname(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @ModelAttribute NicknameAvailabilityRequest request) {
    return CommonResponse.ok(userFacade.checkNickname(principal.getUserId(), request.nickname()));
  }

  @Override
  @GetMapping("/me/notification-settings")
  public CommonResponse<NotificationSettingsResponse> getNotificationSettings(
      @AuthenticationPrincipal UserPrincipal principal) {
    return CommonResponse.ok(userFacade.getNotificationSettings(principal.getUserId()));
  }

  @Override
  @PatchMapping("/me/notification-settings")
  public CommonResponse<?> updateNotificationSettings(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestBody NotificationSettingsUpdateRequest request) {
    userFacade.updateNotificationSettings(principal.getUserId(), request);
    return CommonResponse.ok();
  }

  @Override
  @DeleteMapping("/me")
  public CommonResponse<?> withdraw(@AuthenticationPrincipal UserPrincipal principal) {
    userFacade.withdraw(principal.getUserId());
    return CommonResponse.ok();
  }
}

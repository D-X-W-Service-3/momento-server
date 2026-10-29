package com.momento.server.domain.user.dto.response;

import com.momento.server.domain.user.entity.NotificationSettings;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 설정 조회 응답")
public record NotificationSettingsResponse(
    @Schema(description = "개봉 알림. 예약한 타임캡슐이 열릴 때", example = "true") boolean capsuleOpened,
    @Schema(description = "D-Day 알림. 개봉 7일 전부터 남은 날짜", example = "true") boolean capsuleDDay,
    @Schema(description = "수정 마감 알림. 수정 가능한 기간이 끝나기 전", example = "true") boolean letterDeadline,
    @Schema(description = "새 참여자 알림. 타임캡슐에 누군가 참여했을 때", example = "true") boolean newMember,
    @Schema(description = "추억 다시보기. 지난 추억을 가끔 추천", example = "false") boolean memoryReminder) {

  public static NotificationSettingsResponse from(NotificationSettings settings) {
    return new NotificationSettingsResponse(
        settings.isCapsuleOpened(),
        settings.isCapsuleDDay(),
        settings.isLetterDeadline(),
        settings.isNewMember(),
        settings.isMemoryReminder());
  }
}

package com.momento.server.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 알림 설정 수정 요청. 보낸 항목만 바꾸고, 보내지 않았거나 null 인 항목은 그대로 둔다. */
@Schema(description = "알림 설정 수정 요청. 보낸 항목만 바꾼다.")
public record NotificationSettingsUpdateRequest(
    @Schema(description = "개봉 알림", example = "false") Boolean capsuleOpened,
    @Schema(description = "D-Day 알림") Boolean capsuleDDay,
    @Schema(description = "수정 마감 알림") Boolean letterDeadline,
    @Schema(description = "새 참여자 알림") Boolean newMember,
    @Schema(description = "추억 다시보기") Boolean memoryReminder) {}

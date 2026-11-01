package com.momento.server.domain.timecapsule.dto.request;

import com.momento.server.domain.timecapsule.entity.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 초대 링크 발급·재생성 요청 — 둘의 요청 모양이 같아 하나로 공유한다. */
@Schema(description = "캡슐 초대 링크 발급/재생성 요청")
public record CapsuleInviteCreateRequest(
    @Schema(
            description = "초대할 역할. RECIPIENT 또는 PARTICIPANT 만 허용된다(OWNER 불가). 캡슐 유형에 따라서도 제한된다.",
            example = "PARTICIPANT",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "초대 대상 역할은 필수입니다.")
        MemberRole targetRole) {}

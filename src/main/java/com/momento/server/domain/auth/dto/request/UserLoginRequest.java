package com.momento.server.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "카카오 로그인 요청")
public record UserLoginRequest(
    @Schema(
            description = "프론트에서 카카오 SDK 로 발급받은 액세스 토큰",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "카카오 액세스 토큰은 필수입니다.")
        String accessToken) {}

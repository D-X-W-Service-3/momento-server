package com.momento.server.domain.push.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "웹 푸시 VAPID 공개키")
public record VapidPublicKeyResponse(
    @Schema(
            description = "base64url 공개키. pushManager.subscribe 의 applicationServerKey 로 쓴다.",
            example =
                "BIz1ZbTihDTtOZ7Hwpl6OTeDFhMSpCAdp75U_pUqB6448IK0FlMfs-pg6oU1fAcf2RuJ6KQG3yU3L0PsduNLqPU")
        String publicKey) {}

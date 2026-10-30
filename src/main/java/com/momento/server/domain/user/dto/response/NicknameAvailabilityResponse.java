package com.momento.server.domain.user.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "닉네임 사용 가능 여부 확인 응답")
public record NicknameAvailabilityResponse(
    @Schema(description = "확인한 닉네임. 앞뒤 공백을 뺀 값이다.", example = "상래") String nickname,
    @Schema(description = "사용할 수 있는지. 지금 내 닉네임이면 true 다.", example = "true") boolean available) {}

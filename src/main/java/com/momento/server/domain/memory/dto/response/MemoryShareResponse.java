package com.momento.server.domain.memory.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아카이브 공유 링크 발급 결과")
public record MemoryShareResponse(
    @Schema(
            description = "공유 토큰. 프론트가 공유 링크에 넣고, 받는 사람은 링크로 받기에 이 값을 보낸다. 같은 추억은 늘 같은 값이다.",
            example = "3f0a5c9e2b1d4f7a8c3e9d2b7a6e1f00")
        String shareToken) {}

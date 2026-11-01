package com.momento.server.domain.timecapsule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "타임캡슐 개봉 확인 응답")
public record TimeCapsuleOpenResponse(
    @Schema(description = "타임캡슐 ID", example = "21") Long capsuleId,
    @Schema(description = "내가 이 캡슐을 처음 확인한 일시. 다시 확인해도 바뀌지 않는다.", example = "2027-12-12T09:12:30")
        LocalDateTime openedAt) {}

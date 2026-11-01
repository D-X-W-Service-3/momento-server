package com.momento.server.domain.timecapsule.dto.response;

import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "타임캡슐 생성 응답")
public record TimeCapsuleCreateResponse(
    @Schema(description = "생성된 타임캡슐 ID. 완료 화면의 '캡슐 보러가기' 로 상세에 들어갈 때 쓴다.", example = "12")
        Long capsuleId) {

  public static TimeCapsuleCreateResponse from(TimeCapsule capsule) {
    return new TimeCapsuleCreateResponse(capsule.getId());
  }
}

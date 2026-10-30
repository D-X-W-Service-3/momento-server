package com.momento.server.domain.timecapsule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 하단 탭 '타임캡슐' 첫 화면. 섹션마다 최대 5개이고, "전체보기" 는 목록 조회를 해당 상태로 부른다. */
@Schema(description = "타임캡슐 탭 홈 조회 응답")
public record TimeCapsuleOverviewResponse(
    @Schema(
            description =
                "\"참여 요청받은 타임캡슐이 있어요\" 배너 표시 여부. 내가 링크를 열고 아직 수락 · 거절하지 않은 초대가 있으면 true 다.")
        boolean hasPendingInvite,
    @Schema(description = "곧 만날 타임캡슐. 내가 아직 확인하지 않은 캡슐을 개봉일이 가까운 순으로 최대 5개")
        List<TimeCapsuleCardResponse> upcoming,
    @Schema(description = "수정할 수 있어요. WRITING 캡슐을 편지 마감이 가까운 순으로 최대 5개")
        List<TimeCapsuleCardResponse> writing,
    @Schema(description = "캡슐이 잠겨있어요. LOCKED 캡슐을 개봉일이 가까운 순으로 최대 5개")
        List<TimeCapsuleCardResponse> locked,
    @Schema(description = "추억이 열렸어요. OPENED 캡슐을 최근에 열린 순으로 최대 5개")
        List<TimeCapsuleCardResponse> opened) {}

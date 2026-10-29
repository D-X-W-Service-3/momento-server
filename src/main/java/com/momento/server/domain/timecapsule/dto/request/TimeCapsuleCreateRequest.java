package com.momento.server.domain.timecapsule.dto.request;

import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "타임캡슐 생성 요청")
public record TimeCapsuleCreateRequest(
    @Schema(
            description = "캡슐 대표 아이콘. 값 목록이 확정되기 전까지 문자열로 받는다.",
            example = "ICE_CREAM",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "캡슐 아이콘은 필수입니다.")
        @Size(max = 30, message = "캡슐 아이콘은 30자 이하여야 합니다.")
        String iconType,
    @Schema(
            description = "타임캡슐 이름. 20자 이하.",
            example = "2024 우리, 다시 만나는 날",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "타임캡슐 이름은 필수입니다.")
        @Size(max = 20, message = "타임캡슐 이름은 20자 이하여야 합니다.")
        String title,
    @Schema(description = "타임캡슐 설명. 45자 이하.", example = "1년 동안 서로에게 응원과 사랑을 보냈던 타임캡슐")
        @Size(max = 45, message = "타임캡슐 설명은 45자 이하여야 합니다.")
        String description,
    @Schema(description = "캡슐 유형", example = "GROUP", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "캡슐 유형은 필수입니다.")
        CapsuleType capsuleType,
    @Schema(
            description =
                "편지 공개 범위. RECIPIENT_AND_AUTHOR 는 수신자가 모든 편지를·그 밖의 참여자는 자기가 쓴 편지만,"
                    + " ALL_MEMBERS 는 참여자 전원이 모든 편지를 본다. SELF 캡슐은 ALL_MEMBERS 만 받는다.",
            example = "ALL_MEMBERS",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "공개 범위는 필수입니다.")
        VisibilityType visibilityType,
    @Schema(
            description = "편지 수정 마감일. 이날 23:59:59 에 잠긴다. 오늘부터 개봉일 2일 전까지 고를 수 있다.",
            example = "2027-12-01",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "편지 수정 마감일은 필수입니다.")
        LocalDate letterDeadlineDate,
    @Schema(
            description = "개봉일. 이날 00:00 에 열린다.",
            example = "2027-12-12",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "개봉일은 필수입니다.")
        LocalDate openDate) {}

package com.momento.server.domain.anniversary.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.momento.server.domain.anniversary.entity.RepeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * 기념일 등록 · 수정 요청. 수정도 같은 Body 를 받아 통째로 바꾸므로, 보내지 않은 선택값은 "없음" 이다.
 *
 * <p>문자열은 검증 전에 앞뒤 공백을 빼고, 메모가 비면 null 로 둔다. 알림 시각은 화면이 시 · 분만 고르므로 분 단위로 자른다.
 */
@Schema(description = "기념일 등록 · 수정 요청. 수정은 모든 값을 이 Body 로 바꾼다.")
public record AnniversaryRequest(
    @Schema(
            description = "기념일 이름. 앞뒤 공백을 빼고 50자 이하.",
            example = "우리 100일",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "기념일 이름은 필수입니다.")
        @Size(max = 50, message = "기념일 이름은 50자 이하여야 합니다.")
        String title,
    @Schema(
            description = "기념일 날짜",
            example = "2026-08-12",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "기념일 날짜는 필수입니다.")
        LocalDate anniversaryDate,
    @Schema(
            description = "반복 유형. YEARLY 면 해마다 같은 날이 기념일이다.",
            example = "YEARLY",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "반복 유형은 필수입니다.")
        RepeatType repeatType,
    @Schema(description = "메모. 200자 이하. 비우면 지운다.", example = "처음 만난 카페에 다시 가기")
        @Size(max = 200, message = "메모는 200자 이하여야 합니다.")
        String memo,
    @Schema(
            description = "알림 받기 여부. 끄면 미리 알림도 꺼진다.",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "알림 받기 여부는 필수입니다.")
        Boolean notificationEnabled,
    @Schema(description = "알림 시각. 없으면 09:00. 알림을 꺼도 보관한다.", example = "09:00", type = "string")
        LocalTime notifyTime,
    @Schema(description = "미리 알림. 1 · 3 · 5 · 7 · 10 일 전. 없으면 미리 알림 없음.", example = "1")
        Integer remindDaysBefore) {

  private static final Set<Integer> REMIND_DAYS = Set.of(1, 3, 5, 7, 10);

  public AnniversaryRequest {
    title = title == null ? null : title.strip();
    memo = memo == null || memo.isBlank() ? null : memo.strip();
    notifyTime = notifyTime == null ? null : notifyTime.truncatedTo(ChronoUnit.MINUTES);
  }

  @JsonIgnore
  @Schema(hidden = true)
  @AssertTrue(message = "미리 알림은 1, 3, 5, 7, 10일 전 중에서 선택해야 합니다.")
  public boolean isRemindDaysBeforeAllowed() {
    return remindDaysBefore == null || REMIND_DAYS.contains(remindDaysBefore);
  }
}

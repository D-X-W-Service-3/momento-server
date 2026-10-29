package com.momento.server.domain.anniversary.dto.response;

import com.momento.server.domain.anniversary.entity.Anniversary;
import com.momento.server.domain.anniversary.entity.RepeatType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Schema(description = "기념일 상세 응답. 수정 화면을 채운다.")
public record AnniversaryDetailResponse(
    @Schema(description = "기념일 ID", example = "7") Long anniversaryId,
    @Schema(description = "기념일 이름", example = "우리 100일") String title,
    @Schema(description = "등록한 기념일 날짜", example = "2026-08-12") LocalDate anniversaryDate,
    @Schema(description = "반복 유형", example = "YEARLY") RepeatType repeatType,
    @Schema(description = "메모", nullable = true, example = "처음 만난 카페에 다시 가기") String memo,
    @Schema(description = "알림 받기 여부", example = "true") boolean notificationEnabled,
    @Schema(description = "알림 시각", example = "09:00", type = "string") LocalTime notifyTime,
    @Schema(description = "미리 알림 일수. 없거나 알림 받기가 꺼져 있으면 null.", nullable = true, example = "1")
        Integer remindDaysBefore,
    @Schema(
            description = "다가오는 기념일 날짜. 매년 반복이면 오늘 이후 가장 가까운 날, 반복하지 않으면 등록한 날짜다.",
            example = "2027-08-12")
        LocalDate nextDate,
    @Schema(description = "오늘부터 nextDate 까지 남은 일수. 당일 0, 지나면 음수다.", example = "317") int dDay) {

  /**
   * @param today 서버 시간대의 오늘. 호출하는 쪽이 {@code LocalDate.now(clock)} 으로 넘긴다
   */
  public static AnniversaryDetailResponse of(Anniversary anniversary, LocalDate today) {
    LocalDate nextDate = anniversary.nextDate(today);

    return new AnniversaryDetailResponse(
        anniversary.getId(),
        anniversary.getTitle(),
        anniversary.getAnniversaryDate(),
        anniversary.getRepeatType(),
        anniversary.getMemo(),
        anniversary.isNotificationEnabled(),
        anniversary.getNotifyTime(),
        anniversary.getRemindDaysBefore(),
        nextDate,
        (int) ChronoUnit.DAYS.between(today, nextDate));
  }
}

package com.momento.server.domain.home.dto.response;

import com.momento.server.domain.home.entity.VisitHistory;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleCardResponse;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 앱을 열면 처음 보이는 홈 화면. */
@Schema(description = "홈 조회 응답")
public record HomeResponse(
    @Schema(description = "내 닉네임", example = "상래") String nickname,
    @Schema(description = "안 읽은 알림이 하나라도 있으면 true. 상단 종 아이콘의 점을 가른다.")
        boolean hasUnreadNotification,
    @Schema(description = "가장 먼저 열리는 캡슐. 작성 중 · 잠긴 캡슐 중 개봉일이 가장 가까운 것이고, 없으면 null", nullable = true)
        UpcomingCapsule upcomingCapsule,
    @Schema(description = "타임캡슐 미리보기. 타임캡슐 탭 홈의 \"곧 만날 타임캡슐\" 과 같은 캡슐을 같은 순서로 최대 3개")
        List<TimeCapsuleCardResponse> capsules,
    @Schema(description = "이번 주 방문 기록") WeeklyVisit weeklyVisit) {

  @Schema(description = "가장 먼저 열리는 캡슐")
  public record UpcomingCapsule(
      @Schema(description = "타임캡슐 ID", example = "12") Long capsuleId,
      @Schema(description = "캡슐 이름", example = "스무 살의 나에게") String title,
      @Schema(description = "개봉 일시", example = "2027-03-01T00:00:00") LocalDateTime openAt,
      @Schema(description = "오늘부터 개봉일까지 남은 일수. 당일 0", example = "365") int dDay) {

    /** D-Day 는 캡슐 카드와 같은 계산이다. */
    public static UpcomingCapsule of(TimeCapsule capsule, LocalDate today) {
      return new UpcomingCapsule(
          capsule.getId(),
          capsule.getTitle(),
          capsule.getOpenAt(),
          TimeCapsuleCardResponse.daysUntil(today, capsule.getOpenAt()));
    }
  }

  @Schema(description = "이번 주 방문 기록")
  public record WeeklyVisit(
      @Schema(description = "이번 주 월요일부터 일요일까지 7개") List<Day> days,
      @Schema(description = "오늘부터 거슬러 올라가 끊기기 전까지 들른 일수. 주가 바뀌어도 이어진다.", example = "4")
          int streak) {

    public static WeeklyVisit of(VisitHistory history, LocalDate today) {
      return new WeeklyVisit(
          history.week(today).stream().map(day -> new Day(day.date(), day.visited())).toList(),
          history.streak(today));
    }
  }

  @Schema(description = "방문 기록 하루")
  public record Day(
      @Schema(description = "날짜", example = "2026-10-29") LocalDate date,
      @Schema(description = "이 날 들렀는지") boolean visited) {}
}

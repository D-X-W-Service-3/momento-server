package com.momento.server.domain.calendar.facade;

import com.momento.server.domain.anniversary.service.AnniversaryService;
import com.momento.server.domain.calendar.dto.CalendarType;
import com.momento.server.domain.calendar.dto.request.CalendarDailyRequest;
import com.momento.server.domain.calendar.dto.request.CalendarMonthRequest;
import com.momento.server.domain.calendar.dto.response.CalendarDailyResponse;
import com.momento.server.domain.calendar.dto.response.CalendarMonthResponse;
import com.momento.server.domain.memory.dto.response.MemorySummary;
import com.momento.server.domain.memory.service.MemoryService;
import com.momento.server.domain.timecapsule.dto.response.TimeCapsuleCardResponse;
import com.momento.server.domain.timecapsule.service.TimeCapsuleListService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 캡슐 · 기념일 · 아카이브를 날짜로 모은다. 세 도메인 서비스를 따로 부르고 트랜잭션으로 묶지 않는다. 저장하는 값이 없는 화면 하나의 조회라 서비스끼리 읽는 시점이 조금
 * 어긋나도 괜찮다.
 */
@Component
@RequiredArgsConstructor
public class CalendarFacade {

  private final TimeCapsuleListService timeCapsuleListService;
  private final AnniversaryService anniversaryService;
  private final MemoryService memoryService;
  private final Clock clock;

  /** 점은 고른 종류만 찍고, 개수는 종류와 상관없이 전체 기간으로 센다. */
  public CalendarMonthResponse getMonth(Long userId, CalendarMonthRequest request) {
    int year = request.year();
    int month = request.month();
    LocalDate from = LocalDate.of(year, month, 1);
    LocalDate to = from.plusMonths(1);
    SortedMap<LocalDate, EnumSet<CalendarType>> days = new TreeMap<>();

    if (includes(request.type(), CalendarType.CAPSULE)) {
      mark(days, timeCapsuleListService.getMyOpenDates(userId, from, to), CalendarType.CAPSULE);
    }
    if (includes(request.type(), CalendarType.ANNIVERSARY)) {
      mark(
          days,
          anniversaryService.getAllMine(userId).stream()
              .flatMap(anniversary -> anniversary.dateIn(year).stream())
              .filter(date -> date.getMonthValue() == month)
              .toList(),
          CalendarType.ANNIVERSARY);
    }
    if (includes(request.type(), CalendarType.MEMORY)) {
      mark(days, memoryService.findMyMemoryDates(userId, from, to), CalendarType.MEMORY);
    }

    CalendarMonthResponse.Counts counts =
        CalendarMonthResponse.Counts.of(
            timeCapsuleListService.countMyCapsules(userId),
            memoryService.countMyMemories(userId),
            anniversaryService.countMine(userId));
    return CalendarMonthResponse.of(year, month, counts, days);
  }

  /** 고르지 않은 종류는 조회하지 않고 빈 목록이다. D-Day 는 오늘을 기준으로 계산한다. */
  public CalendarDailyResponse getDaily(Long userId, CalendarDailyRequest request) {
    LocalDate date = request.date();
    LocalDate today = LocalDate.now(clock);

    List<TimeCapsuleCardResponse> capsules =
        includes(request.type(), CalendarType.CAPSULE)
            ? timeCapsuleListService.getMyCapsulesOpeningOn(userId, date).stream()
                .map(
                    summary ->
                        TimeCapsuleCardResponse.of(
                            summary.capsule(),
                            summary.openedByMe(),
                            summary.memberCount(),
                            summary.letterCount(),
                            today))
                .toList()
            : List.of();
    List<CalendarDailyResponse.AnniversaryItem> anniversaries =
        includes(request.type(), CalendarType.ANNIVERSARY)
            ? anniversaryService.getAllMine(userId).stream()
                .filter(
                    anniversary ->
                        anniversary.dateIn(date.getYear()).filter(date::equals).isPresent())
                .map(
                    anniversary ->
                        CalendarDailyResponse.AnniversaryItem.of(anniversary, date, today))
                .toList()
            : List.of();
    List<MemorySummary> memories =
        includes(request.type(), CalendarType.MEMORY)
            ? memoryService.findMyMemoriesOn(userId, date)
            : List.of();

    return new CalendarDailyResponse(date, capsules, anniversaries, memories);
  }

  /** {@code type} 이 null 이면 전체다. */
  private static boolean includes(CalendarType selected, CalendarType type) {
    return selected == null || selected == type;
  }

  private static void mark(
      SortedMap<LocalDate, EnumSet<CalendarType>> days,
      Collection<LocalDate> dates,
      CalendarType type) {
    dates.forEach(
        date -> days.computeIfAbsent(date, d -> EnumSet.noneOf(CalendarType.class)).add(type));
  }
}

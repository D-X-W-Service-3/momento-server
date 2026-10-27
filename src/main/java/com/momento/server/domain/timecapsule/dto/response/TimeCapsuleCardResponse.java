package com.momento.server.domain.timecapsule.dto.response;

import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** 캡슐 카드. 목록 · 탭 홈이 같은 형태를 쓰고, 캘린더 · 통합 검색 · 홈도 이 형태로 내려준다. */
@Schema(description = "타임캡슐 카드")
public record TimeCapsuleCardResponse(
    @Schema(description = "타임캡슐 ID", example = "21") Long capsuleId,
    @Schema(description = "대표 아이콘", example = "ICE_CREAM") String iconType,
    @Schema(description = "캡슐 이름", example = "2024 우리, 다시 만나는 날") String title,
    @Schema(description = "캡슐 설명", example = "1년 동안 서로에게 보낸 응원", nullable = true)
        String description,
    @Schema(description = "캡슐 상태", example = "OPENED") CapsuleStatus status,
    @Schema(description = "내가 개봉 화면을 확인했는지. 열린 캡슐의 \"개봉 가능\"(false) / \"개봉됨\"(true) 배지를 가른다.")
        boolean openedByMe,
    @Schema(description = "개봉 일시", example = "2026-12-12T00:00:00") LocalDateTime openAt,
    @Schema(description = "오늘부터 개봉일까지 남은 일수. 당일 0, 지나면 음수다.", example = "0") int dDay,
    @Schema(
            description = "오늘부터 편지 마감일까지 남은 일수. WRITING 일 때만 값이 있다.",
            example = "3",
            nullable = true)
        Integer deadlineDDay,
    @Schema(description = "참여 중인 인원 수. OWNER 를 포함한다.", example = "5") long memberCount,
    @Schema(description = "제출된 편지 수. 임시 저장과 삭제된 편지는 세지 않는다.", example = "3") long letterCount) {

  /**
   * D-Day 는 시각이 아니라 날짜로 센다. 마감 23:59:59 인 캡슐도 마감일 당일이면 0 이다.
   *
   * @param today 서버 시간대의 오늘. 호출하는 쪽이 {@code LocalDate.now(clock)} 으로 넘긴다
   */
  public static TimeCapsuleCardResponse of(
      TimeCapsule capsule,
      boolean openedByMe,
      long memberCount,
      long letterCount,
      LocalDate today) {
    Integer deadlineDDay =
        capsule.getStatus() == CapsuleStatus.WRITING
            ? daysUntil(today, capsule.getLetterDeadlineAt())
            : null;
    return new TimeCapsuleCardResponse(
        capsule.getId(),
        capsule.getIconType(),
        capsule.getTitle(),
        capsule.getDescription(),
        capsule.getStatus(),
        openedByMe,
        capsule.getOpenAt(),
        daysUntil(today, capsule.getOpenAt()),
        deadlineDDay,
        memberCount,
        letterCount);
  }

  private static int daysUntil(LocalDate today, LocalDateTime target) {
    return (int) ChronoUnit.DAYS.between(today, target.toLocalDate());
  }
}

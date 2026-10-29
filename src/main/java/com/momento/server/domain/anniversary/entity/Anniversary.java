package com.momento.server.domain.anniversary.entity;

import com.momento.server.domain.user.entity.User;
import com.momento.server.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 회원이 등록한 기념일. 캘린더 표시와 알림의 기준이 된다. 삭제하면 행을 지운다. */
@Entity
@Table(name = "anniversaries")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Anniversary extends BaseTimeEntity {

  /** 알림 시각을 고르지 않았을 때 쓰는 시각. */
  public static final LocalTime DEFAULT_NOTIFY_TIME = LocalTime.of(9, 0);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "title", nullable = false, length = 50)
  private String title;

  @Column(name = "anniversary_date", nullable = false)
  private LocalDate anniversaryDate;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "repeat_type", nullable = false, length = 20)
  private RepeatType repeatType = RepeatType.NONE;

  @Column(name = "memo", length = 200)
  private String memo;

  /** 기념일 당일 알림을 받을지. */
  @Column(name = "notification_enabled", nullable = false)
  private boolean notificationEnabled;

  /** 당일 · 미리 알림을 보내는 시각. 알림을 꺼 둔 동안에도 보관해 다시 켜면 이 시각을 쓴다. */
  @Column(name = "notify_time", nullable = false)
  private LocalTime notifyTime;

  /** 며칠 전에 미리 알릴지. 미리 알림이 없거나 알림 받기가 꺼져 있으면 비어 있다. */
  @Column(name = "remind_days_before")
  private Integer remindDaysBefore;

  /** 등록과 수정은 같은 값을 받으므로 같은 규칙({@link #update})을 거친다. */
  public static Anniversary create(
      User user,
      String title,
      LocalDate anniversaryDate,
      RepeatType repeatType,
      String memo,
      boolean notificationEnabled,
      LocalTime notifyTime,
      Integer remindDaysBefore) {
    Anniversary anniversary = Anniversary.builder().user(user).build();
    anniversary.update(
        title,
        anniversaryDate,
        repeatType,
        memo,
        notificationEnabled,
        notifyTime,
        remindDaysBefore);
    return anniversary;
  }

  /**
   * 모든 값을 통째로 바꾼다. null 은 "없음" 이다.
   *
   * <p>알림 받기를 끄면 미리 알림도 꺼진다. 알림 시각은 화면에 늘 표시되고 다시 켜면 그대로 쓰므로 꺼도 보관하고, 비어 있으면 09:00 이다.
   */
  public void update(
      String title,
      LocalDate anniversaryDate,
      RepeatType repeatType,
      String memo,
      boolean notificationEnabled,
      LocalTime notifyTime,
      Integer remindDaysBefore) {
    this.title = title;
    this.anniversaryDate = anniversaryDate;
    this.repeatType = repeatType;
    this.memo = memo;
    this.notificationEnabled = notificationEnabled;
    this.notifyTime = notifyTime == null ? DEFAULT_NOTIFY_TIME : notifyTime;
    this.remindDaysBefore = notificationEnabled ? remindDaysBefore : null;
  }

  /**
   * 오늘 이후(오늘 포함) 다가오는 기념일 날짜.
   *
   * <p>반복하지 않으면 등록한 날짜 그대로이고, 지났으면 과거 날짜다. 매년 반복이면 등록한 날짜가 아직 오지 않았을 때 그 날짜가 첫 기념일이다. 지났으면 올해나 내년의
   * 같은 월 · 일이다. 2월 29일은 {@link LocalDate#withYear} 가 평년에 2월 28일로 맞춘다.
   */
  public LocalDate nextDate(LocalDate today) {
    if (repeatType == RepeatType.NONE || !anniversaryDate.isBefore(today)) {
      return anniversaryDate;
    }
    LocalDate thisYear = anniversaryDate.withYear(today.getYear());
    return thisYear.isBefore(today) ? anniversaryDate.withYear(today.getYear() + 1) : thisYear;
  }
}

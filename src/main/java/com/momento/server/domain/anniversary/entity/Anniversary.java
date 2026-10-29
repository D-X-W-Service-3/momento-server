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

/** 회원이 등록한 기념일. 캘린더 표시와 알림의 기준이 된다. */
@Entity
@Table(name = "anniversaries")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Anniversary extends BaseTimeEntity {

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
}

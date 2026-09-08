package com.momento.server.domain.timecapsule.entity;

import com.momento.server.domain.anniversary.entity.Anniversary;
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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정해진 시각에 열리는 타임캡슐. 삭제는 {@code deletedAt} 을 채우는 소프트 삭제로 처리한다. */
@Entity
@Table(name = "time_capsules")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeCapsule extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "creator_id", nullable = false)
  private User creator;

  /** 기념일에 맞춰 만든 캡슐이면 연결된다. 직접 만든 캡슐이면 비어 있다. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "anniversary_id")
  private Anniversary anniversary;

  @Column(name = "title", nullable = false, length = 50)
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "capsule_type", nullable = false, length = 20)
  private CapsuleType capsuleType;

  @Enumerated(EnumType.STRING)
  @Column(name = "visibility_type", nullable = false, length = 30)
  private VisibilityType visibilityType;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CapsuleStatus status = CapsuleStatus.WRITING;

  @Column(name = "open_at", nullable = false)
  private LocalDateTime openAt;

  /** 편지 작성 마감 일시. 없으면 공개 직전까지 쓸 수 있다. */
  @Column(name = "letter_deadline_at")
  private LocalDateTime letterDeadlineAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;
}

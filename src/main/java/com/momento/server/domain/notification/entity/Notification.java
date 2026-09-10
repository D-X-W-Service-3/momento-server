package com.momento.server.domain.notification.entity;

import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.user.entity.User;
import com.momento.server.global.common.entity.BaseCreatedTimeEntity;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 회원에게 보낸 알림. 발송 후 읽음 여부만 바뀌므로 생성 시각만 관리한다. */
@Entity
@Table(name = "notifications")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseCreatedTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** 기념일 알림처럼 캡슐과 무관한 알림이면 비어 있다. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "time_capsule_id")
  private TimeCapsule timeCapsule;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "notification_type", nullable = false, length = 30)
  private NotificationType notificationType;

  @Column(name = "title", nullable = false, length = 100)
  private String title;

  @Column(name = "content", length = 500)
  private String content;

  @Builder.Default
  @Column(name = "is_read", nullable = false)
  private boolean read = false;

  @Column(name = "read_at")
  private LocalDateTime readAt;
}

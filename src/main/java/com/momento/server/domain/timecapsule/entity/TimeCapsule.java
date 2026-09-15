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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "capsule_type", nullable = false, length = 20)
  private CapsuleType capsuleType;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "visibility_type", nullable = false, length = 30)
  private VisibilityType visibilityType;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", nullable = false, length = 20)
  private CapsuleStatus status = CapsuleStatus.WRITING;

  @Column(name = "open_at", nullable = false)
  private LocalDateTime openAt;

  /** 편지 작성 마감 일시. 없으면 공개 직전까지 쓸 수 있다. */
  @Column(name = "letter_deadline_at")
  private LocalDateTime letterDeadlineAt;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  /**
   * 이 역할의 참여자가 편지함에 들어갈 수 있는지. 캡슐이 열려야 하고, 수신자만 공개인 캡슐이면 수신자만 들어갈 수 있다. 그 밖의 범위에서는 참여자 전원이 들어가되, 어떤
   * 편지가 보이는지는 {@link #canViewLetter} 로 한 통씩 정한다. OWNER 도 예외 없이 편지를 쓴 참여자로 취급한다.
   */
  public boolean canViewLetters(MemberRole role) {
    if (status != CapsuleStatus.OPENED) {
      return false;
    }
    return switch (visibilityType) {
      case RECIPIENT_ONLY -> role == MemberRole.RECIPIENT;
      case PARTICIPANTS_ONLY, ALL_MEMBERS -> true;
    };
  }

  /**
   * 이 역할의 참여자가 편지 한 통을 볼 수 있는지. {@code PARTICIPANTS_ONLY} 는 이름과 달리 "수신자는 전부, 작성자는 자기가 쓴 편지만" 을 뜻한다.
   * 이름이 뜻과 어긋나 있어 별도 작업에서 바꾼다.
   *
   * @param isAuthor 이 편지를 조회하는 회원이 쓴 편지인지
   */
  public boolean canViewLetter(MemberRole role, boolean isAuthor) {
    if (!canViewLetters(role)) {
      return false;
    }
    return switch (visibilityType) {
      case RECIPIENT_ONLY, ALL_MEMBERS -> true;
      case PARTICIPANTS_ONLY -> role == MemberRole.RECIPIENT || isAuthor;
    };
  }
}

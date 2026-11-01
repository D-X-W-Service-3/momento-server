package com.momento.server.domain.timecapsule.entity;

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

/**
 * 타임캡슐 초대 링크.
 *
 * <p>{@code targetRole} 은 {@link MemberRole} 을 재사용하지만 초대로 지정할 수 있는 값은 {@code RECIPIENT} 와 {@code
 * PARTICIPANT} 뿐이다. {@code OWNER} 로 초대할 수 없다는 규칙은 초대 API 에서 막는다.
 *
 * <p>링크는 대상을 정하지 않은 공유 링크라 여러 명이 열고 수락할 수 있다. 누가 열었고 어떻게 응답했는지는 {@link CapsuleInvitee} 에 회원마다 기록한다.
 */
@Entity
@Table(name = "capsule_invites")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CapsuleInvite extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "time_capsule_id", nullable = false)
  private TimeCapsule timeCapsule;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "inviter_id", nullable = false)
  private User inviter;

  @Column(name = "invite_token", nullable = false, unique = true, length = 100)
  private String inviteToken;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "target_role", nullable = false, length = 20)
  private MemberRole targetRole = MemberRole.PARTICIPANT;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", nullable = false, length = 20)
  private InviteStatus status = InviteStatus.ACTIVE;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;

  /** ACTIVE 이고 만료되지 않았는지. 수락 · 거절해도 링크는 그대로이고, 링크를 끝내는 것은 재생성({@link #revoke})뿐이다. */
  public boolean isUsable(LocalDateTime now) {
    return effectiveStatus(now) == InviteStatus.ACTIVE;
  }

  /**
   * 저장된 {@code status} 뿐 아니라 만료 시각도 반영한 실제 상태. 만료를 감지해도 상태를 스케줄러 없이 지연 계산만 하므로, 저장된 값은 그대로 ACTIVE 로
   * 남는다 — 미리보기처럼 "지금 이 링크가 살아있는가"를 보여줄 때는 저장값이 아니라 이 메서드를 쓴다.
   */
  public InviteStatus effectiveStatus(LocalDateTime now) {
    if (status == InviteStatus.ACTIVE && expiresAt != null && !now.isBefore(expiresAt)) {
      return InviteStatus.EXPIRED;
    }
    return status;
  }

  public void revoke() {
    this.status = InviteStatus.REVOKED;
  }
}

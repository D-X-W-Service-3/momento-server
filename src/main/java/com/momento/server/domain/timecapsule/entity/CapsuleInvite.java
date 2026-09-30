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
 * <p>발급 시점에는 누구에게나 열려 있는 공유 링크다({@code invitee} 가 비어 있음). 로그인한 회원이 이 링크로 처음 들어오면(미리보기·수락·거절 중 무엇이든)
 * 그 회원에게 고정된다({@link #claim}) — 초대를 만들 때 대상을 미리 지정하지 않으므로, "이 링크는 누구 것인가"를 서버가 알아야 하는 받은 목록·거절 기능은
 * 이 "선점" 시점부터 성립한다. 한번 고정되면 다른 회원은 이 링크로 참여할 수 없다({@link #isTargetedTo}).
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

  /** 지정 초대 대상. 없으면 공유형 링크다. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "invitee_id")
  private User invitee;

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

  /**
   * ACTIVE 이고 만료되지 않았는지. 링크는 선점되는 순간부터 대상이 한 명으로 고정되고, 수락·거절·취소 무엇으로 끝나든 {@link #revoke} 로 마무리되므로
   * "몇 번 썼는지"를 따로 셀 필요가 없다.
   */
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

  /** 아직 아무도 선점하지 않은 공유 링크인지. */
  public boolean isOpenLink() {
    return invitee == null;
  }

  /** 이 회원에게 고정된 링크인지. 아직 아무도 선점하지 않았으면 false 다(선점부터 해야 한다). */
  public boolean isTargetedTo(Long userId) {
    return invitee != null && invitee.getId().equals(userId);
  }

  /** 아직 선점되지 않은 링크를 이 회원에게 고정한다. 이미 선점됐으면(같은 회원이라도) 아무것도 하지 않는다 — 최초 선점 시각을 보존한다. */
  public void claim(User user) {
    if (invitee == null) {
      this.invitee = user;
    }
  }

  public void revoke() {
    this.status = InviteStatus.REVOKED;
  }
}

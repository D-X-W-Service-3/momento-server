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

/**
 * 타임캡슐 초대 링크.
 *
 * <p>{@code targetRole} 은 {@link MemberRole} 을 재사용하지만 초대로 지정할 수 있는 값은 {@code RECIPIENT} 와 {@code
 * PARTICIPANT} 뿐이다. {@code OWNER} 로 초대할 수 없다는 규칙은 초대 API 에서 막는다.
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
  @Column(name = "target_role", nullable = false, length = 20)
  private MemberRole targetRole = MemberRole.PARTICIPANT;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private InviteStatus status = InviteStatus.ACTIVE;

  /** 최대 사용 횟수. 비어 있으면 제한이 없다. */
  @Column(name = "max_uses")
  private Integer maxUses;

  @Builder.Default
  @Column(name = "used_count", nullable = false)
  private int usedCount = 0;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;
}

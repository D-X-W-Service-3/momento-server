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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 초대 링크를 연 회원. 공유 링크 하나를 여러 사람이 열 수 있어서 (링크, 회원) 마다 한 행이다.
 *
 * <p>링크를 처음 열면 PENDING 으로 만들고, 수락 · 거절하면 상태를 바꾼다. 탭 홈의 초대 배너와 받은 초대 목록은 PENDING 행을 읽는다.
 */
@Entity
@Table(name = "capsule_invitees")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CapsuleInvitee extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "capsule_invite_id", nullable = false)
  private CapsuleInvite capsuleInvite;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", nullable = false, length = 20)
  private InviteeStatus status = InviteeStatus.PENDING;

  /** 거절한 링크로는 다시 참여할 수 없다. 방장이 링크를 재생성하면 새 링크에는 이 행이 없어 다시 받을 수 있다. */
  public boolean isDeclined() {
    return status == InviteeStatus.DECLINED;
  }

  public void accept() {
    this.status = InviteeStatus.ACCEPTED;
  }

  public void decline() {
    this.status = InviteeStatus.DECLINED;
  }
}

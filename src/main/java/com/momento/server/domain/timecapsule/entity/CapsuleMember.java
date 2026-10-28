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

/** 타임캡슐에 참여한 회원. 역할에 따라 볼 수 있는 범위가 달라진다. */
@Entity
@Table(name = "capsule_members")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CapsuleMember extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "time_capsule_id", nullable = false)
  private TimeCapsule timeCapsule;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "role", nullable = false, length = 20)
  private MemberRole role;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", nullable = false, length = 20)
  private MemberStatus status = MemberStatus.ACTIVE;

  @Column(name = "joined_at")
  private LocalDateTime joinedAt;

  /**
   * 이 멤버가 열린 캡슐을 처음 확인한 일시. 아직 확인하지 않았으면 비어 있다. 같은 캡슐이라도 사람마다 "개봉 가능" 과 "개봉됨" 이 갈려 멤버 행에 둔다. 동시에 두
   * 번 확인해도 처음 값이 남도록 조건부 UPDATE 로만 기록한다.
   */
  @Column(name = "opened_at")
  private LocalDateTime openedAt;

  public boolean isOwner() {
    return role == MemberRole.OWNER;
  }

  /** 방장이 내보낸다. 행은 남겨 두고 상태만 바꾼다. 다시 초대받아 들어오면 이 행을 되살린다. */
  public void remove() {
    this.status = MemberStatus.REMOVED;
  }
}

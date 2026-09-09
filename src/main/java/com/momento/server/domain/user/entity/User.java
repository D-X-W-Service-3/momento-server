package com.momento.server.domain.user.entity;

import com.momento.server.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 카카오로 가입한 회원. 탈퇴는 {@code deletedAt} 을 채우는 소프트 삭제로 처리한다. */
@Entity
@Table(name = "users")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "kakao_id", nullable = false, unique = true, length = 100)
  private String kakaoId;

  @Column(name = "nickname", nullable = false, length = 30)
  private String nickname;

  @Column(name = "profile_image_url", length = 500)
  private String profileImageUrl;

  @Builder.Default
  @Column(name = "notification_enabled", nullable = false)
  private boolean notificationEnabled = true;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public boolean isWithdrawn() {
    return deletedAt != null;
  }

  /** 탈퇴 처리. kakao_id 가 유니크라 같은 계정으로 재가입하면 {@link #restore} 로 되살린다. 탈퇴 API 는 별도 이슈에서 붙인다. */
  public void withdraw() {
    this.deletedAt = LocalDateTime.now();
  }

  /** 탈퇴했던 회원이 같은 카카오 계정으로 다시 로그인했을 때 카카오 프로필로 되살린다. */
  public void restore(String nickname, String profileImageUrl) {
    this.deletedAt = null;
    this.nickname = nickname;
    this.profileImageUrl = profileImageUrl;
  }

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }
}

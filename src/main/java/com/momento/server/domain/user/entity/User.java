package com.momento.server.domain.user.entity;

import com.momento.server.global.common.auth.oauth.OAuth2Provider;
import com.momento.server.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인(카카오 OAuth2) 동작에 필요한 최소 필드만 정의한 엔티티. ERD 확정 후 프로필/연관관계 등 도메인 필드를 자유롭게 확장한다. */
@Entity
@Table(name = "users")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "user_id", nullable = false)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "social_provider", nullable = false)
  private OAuth2Provider socialProvider;

  @Column(name = "social_id", nullable = false, unique = true)
  private String socialId;

  @Column private String nickname;

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }
}

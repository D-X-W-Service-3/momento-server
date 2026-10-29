package com.momento.server.domain.user.entity;

import com.momento.server.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
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

  /** 다른 회원과 겹치지 않는다(DB UNIQUE). 탈퇴 회원도 행이 남아 닉네임을 계속 차지한다. */
  @Column(name = "nickname", nullable = false, unique = true, length = 30)
  private String nickname;

  @Column(name = "profile_image_url", length = 500)
  private String profileImageUrl;

  @Column(name = "birthday")
  private LocalDate birthday;

  @Column(name = "email", length = 100)
  private String email;

  /** {@code 010-1234-5678} 처럼 하이픈을 넣은 형식. */
  @Column(name = "phone_number", length = 20)
  private String phoneNumber;

  @Builder.Default @Embedded
  private NotificationSettings notificationSettings = NotificationSettings.defaults();

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  public boolean isWithdrawn() {
    return deletedAt != null;
  }

  /** 탈퇴 처리. kakao_id 가 유니크라 같은 계정으로 재가입하면 {@link #restore} 로 되살린다. 탈퇴 API 는 별도 이슈에서 붙인다. */
  public void withdraw() {
    this.deletedAt = LocalDateTime.now();
  }

  /**
   * 탈퇴했던 회원이 같은 카카오 계정으로 다시 로그인했을 때 되살린다. 닉네임 · 프로필 사진은 탈퇴 전 값을 그대로 둔다. 카카오 프로필로 덮으면 사용자가 정한 값이
   * 사라진다.
   */
  public void restore() {
    this.deletedAt = null;
  }

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }

  public void updateBirthday(LocalDate birthday) {
    this.birthday = birthday;
  }

  /** null 이면 지운다. */
  public void updateEmail(String email) {
    this.email = email;
  }

  /** null 이면 지운다. */
  public void updatePhoneNumber(String phoneNumber) {
    this.phoneNumber = phoneNumber;
  }
}

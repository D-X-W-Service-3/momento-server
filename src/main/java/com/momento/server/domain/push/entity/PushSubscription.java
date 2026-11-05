package com.momento.server.domain.push.entity;

import com.momento.server.domain.user.entity.User;
import com.momento.server.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브라우저 한 곳의 웹 푸시 구독. 한 회원이 기기마다 여러 개를 가질 수 있다.
 *
 * <p>endpoint 는 브라우저가 구독마다 발급하는 고유 주소라 구독을 가리키는 키로 쓴다. 같은 기기에서 다른 계정으로 로그인하면 같은 endpoint 가 다른 회원으로
 * 들어오는데, 이때는 소유자를 바꾼다. 이전 회원의 알림이 그 기기에 계속 뜨지 않게 하기 위해서다.
 */
@Entity
@Table(
    name = "push_subscriptions",
    uniqueConstraints =
        @UniqueConstraint(name = "UK_PUSH_SUBSCRIPTIONS_ENDPOINT", columnNames = "endpoint"))
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscription extends BaseTimeEntity {

  /** utf8mb4 에서 인덱스 키 한도(3072바이트)에 들어가는 최대 길이. 실제 endpoint 는 ASCII 라 훨씬 짧다. */
  public static final int ENDPOINT_MAX_LENGTH = 768;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** 푸시 서비스 주소. 허용하는 호스트는 {@link PushEndpoint} 가 정한다. */
  @Column(name = "endpoint", nullable = false, length = ENDPOINT_MAX_LENGTH)
  private String endpoint;

  /** 페이로드 암호화에 쓰는 브라우저 공개키(base64url). */
  @Column(name = "p256dh", nullable = false, length = 255)
  private String p256dh;

  /** 페이로드 암호화에 쓰는 인증 비밀값(base64url). */
  @Column(name = "auth", nullable = false, length = 255)
  private String auth;

  /** 같은 endpoint 로 다시 구독하면 소유자와 키를 새 값으로 바꾼다. */
  public void renew(User user, String p256dh, String auth) {
    this.user = user;
    this.p256dh = p256dh;
    this.auth = auth;
  }
}

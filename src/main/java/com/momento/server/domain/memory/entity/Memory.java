package com.momento.server.domain.memory.entity;

import com.momento.server.domain.timecapsule.entity.TimeCapsule;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 아카이빙된 추억. 삭제는 {@code deletedAt} 을 채우는 소프트 삭제로 처리한다. */
@Entity
@Table(name = "memories")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Memory extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  /** 열린 타임캡슐에서 옮겨 온 추억이면 연결된다. 직접 등록한 추억이면 비어 있다. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "time_capsule_id")
  private TimeCapsule timeCapsule;

  @Column(name = "title", nullable = false, length = 100)
  private String title;

  @Column(name = "content", columnDefinition = "TEXT")
  private String content;

  @Column(name = "memory_date", nullable = false)
  private LocalDate memoryDate;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "visibility_type", nullable = false, length = 30)
  private MemoryVisibilityType visibilityType = MemoryVisibilityType.PRIVATE;

  /** 공개 범위가 LINK 일 때 발급되는 조회 토큰. */
  @Column(name = "share_token", unique = true, length = 255)
  private String shareToken;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  /** 보낸 값만 바꾼다. null 인 값은 그대로 둔다(명세: 보낸 경우에만 수정). */
  public void update(String title, String content, LocalDate memoryDate) {
    if (title != null) {
      this.title = title;
    }
    if (content != null) {
      this.content = content;
    }
    if (memoryDate != null) {
      this.memoryDate = memoryDate;
    }
  }

  /**
   * 공유 링크를 연다. 처음이면 토큰을 만들고 공개 범위를 LINK 로 바꾼다. 이미 공유했으면 같은 토큰을 그대로 쓴다 — 링크 하나를 여러 사람이 받는다.
   *
   * @return 공유 토큰
   */
  public String share(Supplier<String> newToken) {
    if (shareToken == null) {
      this.shareToken = newToken.get();
      this.visibilityType = MemoryVisibilityType.LINK;
    }
    return shareToken;
  }

  public boolean isOwnedBy(Long userId) {
    return user.getId().equals(userId);
  }

  /** 소프트 삭제. 아카이브 목록 · 캘린더 · 상세 쿼리가 모두 {@code deletedAt} 이 빈 추억만 읽는다. */
  public void delete(LocalDateTime now) {
    this.deletedAt = now;
  }
}

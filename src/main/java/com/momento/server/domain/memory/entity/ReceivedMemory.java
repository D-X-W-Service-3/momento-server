package com.momento.server.domain.memory.entity;

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
 * 공유 링크로 받은 아카이브. 추억 하나의 링크를 여러 사람이 열 수 있어서 (추억, 받은 회원) 마다 한 행이다.
 *
 * <p>저장은 복사가 아니라 이 행이 원본 추억을 가리키는 것이다. 원본이 수정되면 같이 바뀌고, 삭제되면 받은 사람 쪽에서도 사라진다.
 */
@Entity
@Table(name = "received_memories")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceivedMemory extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "memory_id", nullable = false)
  private Memory memory;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "receiver_id", nullable = false)
  private User receiver;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "status", nullable = false, length = 20)
  private ReceivedMemoryStatus status = ReceivedMemoryStatus.PENDING;

  public boolean isPending() {
    return status == ReceivedMemoryStatus.PENDING;
  }

  public void save() {
    this.status = ReceivedMemoryStatus.SAVED;
  }

  public void dismiss() {
    this.status = ReceivedMemoryStatus.DISMISSED;
  }

  /** 닫은 아카이브의 링크를 다시 열면 다시 받겠다는 뜻이라 받은 아카이브에 되돌린다. 저장한 아카이브는 그대로 둔다. */
  public void reopen() {
    if (status == ReceivedMemoryStatus.DISMISSED) {
      this.status = ReceivedMemoryStatus.PENDING;
    }
  }
}

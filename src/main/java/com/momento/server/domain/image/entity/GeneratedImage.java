package com.momento.server.domain.image.entity;

import com.momento.server.domain.letter.entity.Letter;
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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 별도 AI 서버가 편지를 바탕으로 만든 이미지. 요청 시점에 PENDING 으로 만들고 결과가 오면 채운다. */
@Entity
@Table(name = "generated_images")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeneratedImage extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "time_capsule_id", nullable = false)
  private TimeCapsule timeCapsule;

  /** 편지 한 통을 기준으로 만든 이미지면 연결된다. 캡슐 전체를 종합해 만들었으면 비어 있다. */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "letter_id")
  private Letter letter;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "requester_id")
  private User requester;

  @Column(name = "prompt", columnDefinition = "TEXT")
  private String prompt;

  /** 생성이 끝나기 전에는 비어 있다. */
  @Column(name = "image_url", length = 500)
  private String imageUrl;

  @Builder.Default
  @Column(name = "is_selected", nullable = false)
  private boolean selected = false;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.VARCHAR)
  @Column(name = "generation_status", nullable = false, length = 20)
  private GenerationStatus generationStatus = GenerationStatus.PENDING;
}

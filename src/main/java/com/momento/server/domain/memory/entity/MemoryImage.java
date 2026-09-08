package com.momento.server.domain.memory.entity;

import com.momento.server.global.common.entity.BaseCreatedTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 추억에 첨부된 S3 이미지. 등록 후 수정하지 않아 생성 시각만 관리한다. */
@Entity
@Table(name = "memory_images")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemoryImage extends BaseCreatedTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "memory_id", nullable = false)
  private Memory memory;

  @Column(name = "image_url", nullable = false, length = 500)
  private String imageUrl;

  @Builder.Default
  @Column(name = "display_order", nullable = false)
  private int displayOrder = 0;
}

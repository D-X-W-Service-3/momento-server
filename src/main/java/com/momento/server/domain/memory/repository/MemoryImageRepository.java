package com.momento.server.domain.memory.repository;

import com.momento.server.domain.memory.entity.MemoryImage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryImageRepository extends JpaRepository<MemoryImage, Long> {

  /** 추억 하나에 붙은 이미지 전체를 표시 순서대로 가져온다. 등록 직후 응답을 만들 때 쓴다. */
  List<MemoryImage> findByMemoryIdOrderByDisplayOrderAsc(Long memoryId);

  /** 여러 추억의 이미지를 쿼리 한 번으로 가져온다. 목록 조회에서 대표 이미지를 뽑을 때 쓴다. */
  List<MemoryImage> findByMemoryIdInOrderByDisplayOrderAsc(List<Long> memoryIds);

  /** 아카이브 수정에서 사진 목록을 통째로 바꾸기 전에 기존 사진을 지운다. */
  void deleteByMemoryId(Long memoryId);
}

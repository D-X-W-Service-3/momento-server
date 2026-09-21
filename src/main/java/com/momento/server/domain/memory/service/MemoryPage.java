package com.momento.server.domain.memory.service;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

/** 추억 목록 조회의 서비스 결과. 추억 한 페이지와, 그 추억들의 이미지를 추억 ID 별로 묶은 것을 함께 담는다. */
public record MemoryPage(Page<Memory> memories, Map<Long, List<MemoryImage>> imagesByMemory) {

  /** 이미지가 없는 추억은 빈 목록을 돌려준다. */
  public List<MemoryImage> imagesOf(Memory memory) {
    return imagesByMemory.getOrDefault(memory.getId(), List.of());
  }
}

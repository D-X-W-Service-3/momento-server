package com.momento.server.domain.memory.facade;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.response.MemoryListResponse;
import com.momento.server.domain.memory.dto.response.MemoryResponse;
import com.momento.server.domain.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryFacade {

  private final MemoryService memoryService;

  public MemoryResponse create(Long userId, MemoryCreateRequest request) {
    return memoryService.create(userId, request);
  }

  public MemoryListResponse findMyMemories(
      Long userId, String title, Integer year, int page, int size) {
    return memoryService.findMyMemories(userId, title, year, page, size);
  }
}

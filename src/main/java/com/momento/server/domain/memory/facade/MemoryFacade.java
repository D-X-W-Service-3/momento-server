package com.momento.server.domain.memory.facade;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.response.MemoryListResponse;
import com.momento.server.domain.memory.dto.response.MemoryResponse;
import com.momento.server.domain.memory.dto.response.MemorySummary;
import com.momento.server.domain.memory.service.MemoryDetail;
import com.momento.server.domain.memory.service.MemoryPage;
import com.momento.server.domain.memory.service.MemoryService;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryFacade {

  private final UserService userService;
  private final MemoryService memoryService;

  public MemoryResponse create(Long userId, MemoryCreateRequest request) {
    User user = userService.getActiveUser(userId);
    MemoryDetail detail = memoryService.create(user, request);

    return MemoryResponse.of(detail.memory(), detail.images());
  }

  public MemoryListResponse findMyMemories(
      Long userId, String title, Integer year, int page, int size) {
    MemoryPage result = memoryService.findMyMemories(userId, title, year, page, size);

    List<MemorySummary> summaries =
        result.memories().getContent().stream()
            .map(memory -> MemorySummary.of(memory, result.imagesOf(memory)))
            .toList();

    return MemoryListResponse.of(result.memories(), summaries);
  }
}

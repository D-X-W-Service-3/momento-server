package com.momento.server.domain.memory.facade;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.response.MemoryListResponse;
import com.momento.server.domain.memory.dto.response.MemorySummary;
import com.momento.server.domain.memory.service.MemoryPage;
import com.momento.server.domain.memory.service.MemoryService;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.service.UserService;
import com.momento.server.global.common.storage.PresignedUrlProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryFacade {

  private final UserService userService;
  private final MemoryService memoryService;
  private final PresignedUrlProvider presignedUrlProvider;

  /** 등록 후 아카이브 목록으로 돌아가 목록을 다시 조회하므로 돌려주는 값이 없다. */
  public void create(Long userId, MemoryCreateRequest request) {
    User user = userService.getActiveUser(userId);
    memoryService.create(user, request);
  }

  public MemoryListResponse findMyMemories(
      Long userId, String keyword, Integer year, int page, int size) {
    MemoryPage result = memoryService.findMyMemories(userId, keyword, year, page, size);

    List<MemorySummary> summaries =
        result.memories().getContent().stream()
            .map(
                memory ->
                    MemorySummary.of(
                        memory, result.imagesOf(memory), presignedUrlProvider::viewUrl))
            .toList();

    return MemoryListResponse.of(result.memories(), summaries);
  }
}

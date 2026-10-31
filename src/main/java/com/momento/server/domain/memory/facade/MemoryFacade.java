package com.momento.server.domain.memory.facade;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.request.MemoryUpdateRequest;
import com.momento.server.domain.memory.dto.response.MemoryDetailResponse;
import com.momento.server.domain.memory.dto.response.MemoryListResponse;
import com.momento.server.domain.memory.dto.response.MemorySummary;
import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.service.MemoryDetail;
import com.momento.server.domain.memory.service.MemoryPage;
import com.momento.server.domain.memory.service.MemoryService;
import com.momento.server.domain.memory.service.MemoryShareService;
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
  private final MemoryShareService memoryShareService;
  private final PresignedUrlProvider presignedUrlProvider;

  /** 등록 후 아카이브 목록으로 돌아가 목록을 다시 조회하므로 돌려주는 값이 없다. */
  public void create(Long userId, MemoryCreateRequest request) {
    User user = userService.getActiveUser(userId);
    memoryService.create(user, request);
  }

  /** 저장한 추억이면 작성자는 공유한 사람이고 isMine 은 false 다. 수정 · 삭제는 작성자만 할 수 있다. */
  public MemoryDetailResponse getDetail(Long memoryId, Long userId) {
    MemoryDetail detail = memoryService.getDetail(memoryId, userId);
    Memory memory = detail.memory();

    return MemoryDetailResponse.of(
        memory,
        detail.images(),
        memory.getUser().getNickname(),
        memory.isOwnedBy(userId),
        presignedUrlProvider::viewUrl);
  }

  public void update(Long memoryId, Long userId, MemoryUpdateRequest request) {
    memoryService.update(memoryId, userId, request);
  }

  public void delete(Long memoryId, Long userId) {
    memoryService.delete(memoryId, userId);
  }

  public MemoryListResponse findMyMemories(
      Long userId, String keyword, Integer year, int page, int size) {
    MemoryPage result = memoryService.findMyMemories(userId, keyword, year, page, size);

    List<MemorySummary> summaries =
        result.memories().getContent().stream()
            .map(
                memory ->
                    MemorySummary.of(
                        memory,
                        result.imagesOf(memory),
                        memory.getUser().getNickname(),
                        presignedUrlProvider::viewUrl))
            .toList();

    return MemoryListResponse.of(
        result.memories(), summaries, memoryShareService.hasReceived(userId));
  }
}

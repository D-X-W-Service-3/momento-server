package com.momento.server.domain.memory.facade;

import com.momento.server.domain.memory.dto.response.MemoryShareResponse;
import com.momento.server.domain.memory.dto.response.ReceivedMemoryListResponse;
import com.momento.server.domain.memory.service.MemoryShareService;
import com.momento.server.domain.memory.service.ReceivedMemories;
import com.momento.server.global.common.storage.PresignedUrlProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryShareFacade {

  private final MemoryShareService memoryShareService;
  private final PresignedUrlProvider presignedUrlProvider;

  public MemoryShareResponse share(Long memoryId, Long userId) {
    return new MemoryShareResponse(memoryShareService.share(memoryId, userId));
  }

  public void receive(String shareToken, Long userId) {
    memoryShareService.receive(shareToken, userId);
  }

  public ReceivedMemoryListResponse getReceived(Long userId) {
    ReceivedMemories received = memoryShareService.getReceived(userId);
    return ReceivedMemoryListResponse.of(
        received.received(), received.imagesByMemory(), presignedUrlProvider::viewUrl);
  }

  public void save(Long receivedMemoryId, Long userId) {
    memoryShareService.save(receivedMemoryId, userId);
  }

  public void dismiss(Long receivedMemoryId, Long userId) {
    memoryShareService.dismiss(receivedMemoryId, userId);
  }
}

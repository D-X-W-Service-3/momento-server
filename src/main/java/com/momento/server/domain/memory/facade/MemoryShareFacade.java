package com.momento.server.domain.memory.facade;

import com.momento.server.domain.memory.dto.response.MemoryShareResponse;
import com.momento.server.domain.memory.service.MemoryShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryShareFacade {

  private final MemoryShareService memoryShareService;

  public MemoryShareResponse share(Long memoryId, Long userId) {
    return new MemoryShareResponse(memoryShareService.share(memoryId, userId));
  }

  public void receive(String shareToken, Long userId) {
    memoryShareService.receive(shareToken, userId);
  }
}

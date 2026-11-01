package com.momento.server.domain.memory.controller;

import com.momento.server.domain.memory.dto.response.ReceivedMemoryListResponse;
import com.momento.server.domain.memory.facade.MemoryShareFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.code.SuccessCode;
import com.momento.server.global.common.dto.CommonResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@RestApiController("/v1/memories/received")
@RequiredArgsConstructor
public class ReceivedMemoryController implements ReceivedMemoryApi {

  private final MemoryShareFacade memoryShareFacade;

  @Override
  @GetMapping
  public CommonResponse<ReceivedMemoryListResponse> getReceivedMemories(
      @AuthenticationPrincipal UserPrincipal principal) {
    return CommonResponse.ok(memoryShareFacade.getReceived(principal.getUserId()));
  }

  @Override
  @PostMapping("/{receivedMemoryId}/save")
  public CommonResponse<?> saveReceivedMemory(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long receivedMemoryId) {
    memoryShareFacade.save(receivedMemoryId, principal.getUserId());
    return CommonResponse.success(SuccessCode.CREATED);
  }

  @Override
  @PostMapping("/{receivedMemoryId}/dismiss")
  public CommonResponse<?> dismissReceivedMemory(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long receivedMemoryId) {
    memoryShareFacade.dismiss(receivedMemoryId, principal.getUserId());
    return CommonResponse.ok();
  }
}

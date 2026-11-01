package com.momento.server.domain.memory.controller;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.request.MemoryUpdateRequest;
import com.momento.server.domain.memory.dto.response.MemoryDetailResponse;
import com.momento.server.domain.memory.dto.response.MemoryListResponse;
import com.momento.server.domain.memory.dto.response.MemoryShareResponse;
import com.momento.server.domain.memory.facade.MemoryFacade;
import com.momento.server.domain.memory.facade.MemoryShareFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.code.SuccessCode;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestApiController("/v1/memories")
@RequiredArgsConstructor
public class MemoryController implements MemoryApi {

  private final MemoryFacade memoryFacade;
  private final MemoryShareFacade memoryShareFacade;

  @Override
  @PostMapping
  public CommonResponse<?> create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody MemoryCreateRequest request) {
    memoryFacade.create(principal.getUserId(), request);
    return CommonResponse.success(SuccessCode.CREATED);
  }

  @Override
  @GetMapping("/{memoryId}")
  public CommonResponse<MemoryDetailResponse> getMemory(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long memoryId) {
    return CommonResponse.ok(memoryFacade.getDetail(memoryId, principal.getUserId()));
  }

  @Override
  @PatchMapping("/{memoryId}")
  public CommonResponse<?> updateMemory(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long memoryId,
      @Valid @RequestBody MemoryUpdateRequest request) {
    memoryFacade.update(memoryId, principal.getUserId(), request);
    return CommonResponse.ok();
  }

  @Override
  @DeleteMapping("/{memoryId}")
  public CommonResponse<?> deleteMemory(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long memoryId) {
    memoryFacade.delete(memoryId, principal.getUserId());
    return CommonResponse.ok();
  }

  @Override
  @GetMapping
  public CommonResponse<MemoryListResponse> findMyMemories(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer year,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return CommonResponse.ok(
        memoryFacade.findMyMemories(principal.getUserId(), keyword, year, page, size));
  }

  @Override
  @PostMapping("/{memoryId}/share")
  public CommonResponse<MemoryShareResponse> shareMemory(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long memoryId) {
    return CommonResponse.ok(memoryShareFacade.share(memoryId, principal.getUserId()));
  }

  @Override
  @PostMapping("/shared/{shareToken}")
  public CommonResponse<?> receiveSharedMemory(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable String shareToken) {
    memoryShareFacade.receive(shareToken, principal.getUserId());
    return CommonResponse.ok();
  }
}

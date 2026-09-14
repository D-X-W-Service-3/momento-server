package com.momento.server.domain.memory.controller;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.response.MemoryListResponse;
import com.momento.server.domain.memory.dto.response.MemoryResponse;
import com.momento.server.domain.memory.facade.MemoryFacade;
import com.momento.server.global.common.annotation.RestApiController;
import com.momento.server.global.common.auth.UserPrincipal;
import com.momento.server.global.common.code.SuccessCode;
import com.momento.server.global.common.dto.CommonResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@RestApiController("/v1/memories")
@RequiredArgsConstructor
public class MemoryController implements MemoryApi {

  private final MemoryFacade memoryFacade;

  @Override
  @PostMapping
  public CommonResponse<MemoryResponse> create(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody MemoryCreateRequest request) {
    return CommonResponse.success(
        SuccessCode.CREATED, memoryFacade.create(principal.getUserId(), request));
  }

  @Override
  @GetMapping
  public CommonResponse<MemoryListResponse> findMyMemories(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) String title,
      @RequestParam(required = false) Integer year,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return CommonResponse.ok(
        memoryFacade.findMyMemories(principal.getUserId(), title, year, page, size));
  }
}

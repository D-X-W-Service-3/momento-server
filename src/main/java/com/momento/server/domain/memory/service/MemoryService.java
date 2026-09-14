package com.momento.server.domain.memory.service;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.response.MemoryListResponse;
import com.momento.server.domain.memory.dto.response.MemoryResponse;
import com.momento.server.domain.memory.dto.response.MemorySummary;
import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import com.momento.server.domain.memory.repository.MemoryImageRepository;
import com.momento.server.domain.memory.repository.MemoryRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.domain.user.exception.UserErrorCode;
import com.momento.server.domain.user.repository.UserRepository;
import com.momento.server.global.common.exception.ApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemoryService {

  private static final int MAX_PAGE_SIZE = 100;

  private final MemoryRepository memoryRepository;
  private final MemoryImageRepository memoryImageRepository;
  private final UserRepository userRepository;

  /** 추억 하나와 첨부 이미지를 저장한다. 공개 범위는 요청으로 받지 않고 엔티티 기본값(PRIVATE)을 쓴다. */
  @Transactional
  public MemoryResponse create(Long userId, MemoryCreateRequest request) {
    User user =
        userRepository
            .findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new ApiException(UserErrorCode.USER_NOT_FOUND));

    Memory memory =
        memoryRepository.save(
            Memory.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .memoryDate(request.memoryDate())
                .build());

    List<String> imageUrls = request.imageUrlsOrEmpty();
    saveImages(memory, imageUrls);

    return MemoryResponse.of(memory, imageUrls);
  }

  /** 보낸 순서를 display_order 0, 1, 2... 로 저장한다. 0번이 목록의 대표 이미지가 된다. */
  private void saveImages(Memory memory, List<String> imageUrls) {
    List<MemoryImage> images = new ArrayList<>();

    for (int order = 0; order < imageUrls.size(); order++) {
      images.add(
          MemoryImage.builder()
              .memory(memory)
              .imageUrl(imageUrls.get(order))
              .displayOrder(order)
              .build());
    }

    memoryImageRepository.saveAll(images);
  }

  /** 아카이브 탭 목록. 연도는 그 해의 1월 1일 ~ 12월 31일 범위로 바꿔 넘긴다. */
  public MemoryListResponse findMyMemories(
      Long userId, String title, Integer year, int page, int size) {

    String keyword = (title == null || title.isBlank()) ? null : title.trim();
    LocalDate startDate = (year == null) ? null : LocalDate.of(year, 1, 1);
    LocalDate endDate = (year == null) ? null : LocalDate.of(year, 12, 31);

    Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
    Page<Memory> memories =
        memoryRepository.findMyMemories(userId, keyword, startDate, endDate, pageable);

    Map<Long, String> thumbnails = findThumbnails(memories.getContent());
    List<MemorySummary> summaries =
        memories.getContent().stream()
            .map(memory -> MemorySummary.of(memory, thumbnails.get(memory.getId())))
            .toList();

    return MemoryListResponse.of(memories, summaries);
  }

  private int clampSize(int size) {
    if (size < 1) {
      return 1;
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }

  /** 추억 여러 건의 대표 이미지를 쿼리 한 번으로 모아 온다. 이미지가 없는 추억은 Map 에 들어가지 않는다. */
  private Map<Long, String> findThumbnails(List<Memory> memories) {
    if (memories.isEmpty()) {
      return Map.of();
    }

    List<Long> memoryIds = memories.stream().map(Memory::getId).toList();
    List<MemoryImage> images =
        memoryImageRepository.findByMemoryIdInOrderByDisplayOrderAsc(memoryIds);

    Map<Long, String> thumbnails = new HashMap<>();
    for (MemoryImage image : images) {
      thumbnails.putIfAbsent(image.getMemory().getId(), image.getImageUrl());
    }

    return thumbnails;
  }
}

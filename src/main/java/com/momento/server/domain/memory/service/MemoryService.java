package com.momento.server.domain.memory.service;

import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import com.momento.server.domain.memory.repository.MemoryImageRepository;
import com.momento.server.domain.memory.repository.MemoryRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.global.common.code.GlobalErrorCode;
import com.momento.server.global.common.exception.ApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
  private static final int MIN_YEAR = 1900;
  private static final int MAX_YEAR = 2100;

  private final MemoryRepository memoryRepository;
  private final MemoryImageRepository memoryImageRepository;

  /** 추억 하나와 첨부 이미지를 저장한다. 공개 범위는 요청으로 받지 않고 엔티티 기본값(PRIVATE)을 쓴다. */
  @Transactional
  public MemoryDetail create(User user, MemoryCreateRequest request) {
    Memory memory =
        memoryRepository.save(
            Memory.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .memoryDate(request.memoryDate())
                .build());

    List<MemoryImage> images = saveImages(memory, request.imageUrlsOrEmpty());

    return new MemoryDetail(memory, images);
  }

  /** 보낸 순서를 display_order 0, 1, 2... 로 저장한다. 0번이 목록의 대표 이미지가 된다. */
  private List<MemoryImage> saveImages(Memory memory, List<String> imageUrls) {
    List<MemoryImage> images = new ArrayList<>();

    for (int order = 0; order < imageUrls.size(); order++) {
      images.add(
          MemoryImage.builder()
              .memory(memory)
              .imageUrl(imageUrls.get(order))
              .displayOrder(order)
              .build());
    }

    return memoryImageRepository.saveAll(images);
  }

  /** 아카이브 탭 목록. 연도는 그 해의 1월 1일 ~ 12월 31일 범위로 바꿔 넘긴다. */
  public MemoryPage findMyMemories(Long userId, String title, Integer year, int page, int size) {
    String keyword = (title == null || title.isBlank()) ? null : title.trim();
    LocalDate startDate = null;
    LocalDate endDate = null;

    if (year != null) {
      validateYear(year);
      startDate = LocalDate.of(year, 1, 1);
      endDate = LocalDate.of(year, 12, 31);
    }

    Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
    Page<Memory> memories =
        memoryRepository.findMyMemories(userId, keyword, startDate, endDate, pageable);

    return new MemoryPage(memories, findImagesByMemory(memories.getContent()));
  }

  /** 추억은 사람이 겪은 일이라 1900~2100 밖의 연도는 오타로 본다. LocalDate.of() 예외로 500 이 나는 것도 함께 막는다. */
  private void validateYear(int year) {
    if (year < MIN_YEAR || year > MAX_YEAR) {
      throw new ApiException(GlobalErrorCode.INVALID_INPUT_VALUE);
    }
  }

  private int clampSize(int size) {
    if (size < 1) {
      return 1;
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }

  /** 추억 여러 건의 이미지를 쿼리 한 번으로 모아 온다. display_order 오름차순이라 각 목록의 첫 번째가 대표 이미지다. */
  private Map<Long, List<MemoryImage>> findImagesByMemory(List<Memory> memories) {
    if (memories.isEmpty()) {
      return Map.of();
    }

    List<Long> memoryIds = memories.stream().map(Memory::getId).toList();

    return memoryImageRepository.findByMemoryIdInOrderByDisplayOrderAsc(memoryIds).stream()
        .collect(Collectors.groupingBy(image -> image.getMemory().getId()));
  }
}

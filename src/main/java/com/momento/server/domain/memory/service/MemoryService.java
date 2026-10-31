package com.momento.server.domain.memory.service;

import com.momento.server.domain.image.entity.ImageTarget;
import com.momento.server.domain.image.service.ImageKeys;
import com.momento.server.domain.memory.dto.request.MemoryCreateRequest;
import com.momento.server.domain.memory.dto.request.MemoryUpdateRequest;
import com.momento.server.domain.memory.dto.response.MemorySummary;
import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import com.momento.server.domain.memory.exception.MemoryErrorCode;
import com.momento.server.domain.memory.repository.MemoryImageRepository;
import com.momento.server.domain.memory.repository.MemoryRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.global.common.code.GlobalErrorCode;
import com.momento.server.global.common.exception.ApiException;
import com.momento.server.global.common.storage.PresignedUrlProvider;
import com.momento.server.global.common.util.LikePattern;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
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
  private final PresignedUrlProvider presignedUrlProvider;
  private final Clock clock;

  /** 추억 하나와 첨부 이미지를 저장한다. 공개 범위는 요청으로 받지 않고 엔티티 기본값(PRIVATE)을 쓴다. */
  @Transactional
  public void create(User user, MemoryCreateRequest request) {
    List<String> imageKeys =
        arrangeImageKeys(
            user.getId(), request.imageKeysOrEmpty(), request.representativeImageKey());

    Memory memory =
        memoryRepository.save(
            Memory.builder()
                .user(user)
                .title(request.title())
                .content(request.content())
                .memoryDate(request.memoryDate())
                .build());

    saveImages(memory, imageKeys);
  }

  /** 아카이브 상세. 사진은 표시 순서대로다. */
  public MemoryDetail getDetail(Long memoryId, Long userId) {
    Memory memory = getMine(memoryId, userId);

    return new MemoryDetail(
        memory, memoryImageRepository.findByMemoryIdOrderByDisplayOrderAsc(memory.getId()));
  }

  /**
   * 보낸 값만 바꾼다. imageKeys 를 보내면 사진 목록을 그 값으로 바꾸고, 대표 사진 key 만 보내면 기존 사진 중 그 사진을 첫 번째로 옮긴다. 사진 검증 규칙은
   * 등록과 같다.
   */
  @Transactional
  public void update(Long memoryId, Long userId, MemoryUpdateRequest request) {
    Memory memory = getMine(memoryId, userId);

    if (request.changesImages()) {
      List<MemoryImage> current =
          memoryImageRepository.findByMemoryIdOrderByDisplayOrderAsc(memory.getId());
      List<String> imageKeys =
          request.imageKeys() != null
              ? request.imageKeys()
              : current.stream().map(MemoryImage::getImageUrl).toList();
      List<String> arranged = arrangeImageKeys(userId, imageKeys, request.representativeImageKey());

      replaceImages(memory, current, arranged);
    }

    memory.update(request.title(), request.content(), request.memoryDate());
  }

  /** 소프트 삭제. 사진 행과 S3 파일은 남겨 둔다. */
  @Transactional
  public void delete(Long memoryId, Long userId) {
    getMine(memoryId, userId).delete(LocalDateTime.now(clock));
  }

  /**
   * 바뀐 사진만 반영한다. 목록에서 빠진 사진은 지우고, 남은 사진은 순서만 바꾸고, 새 사진만 추가한다. 행을 모두 지우고 다시 만들면 대표 사진만 바꿔도 상세 응답의
   * imageId 가 수정할 때마다 바뀐다.
   */
  private void replaceImages(Memory memory, List<MemoryImage> current, List<String> arranged) {
    Map<String, MemoryImage> currentByKey =
        current.stream().collect(Collectors.toMap(MemoryImage::getImageUrl, image -> image));

    memoryImageRepository.deleteAll(
        current.stream().filter(image -> !arranged.contains(image.getImageUrl())).toList());

    List<MemoryImage> added = new ArrayList<>();
    for (int order = 0; order < arranged.size(); order++) {
      String key = arranged.get(order);
      MemoryImage existing = currentByKey.get(key);

      if (existing != null) {
        existing.changeDisplayOrder(order);
      } else {
        added.add(MemoryImage.builder().memory(memory).imageUrl(key).displayOrder(order).build());
      }
    }

    memoryImageRepository.saveAll(added);
  }

  /** 내 아카이브를 찾는다. 없는 아카이브 · 삭제된 아카이브 · 다른 회원의 아카이브 · 타임캡슐에서 옮겨진 추억은 똑같이 404 다. */
  private Memory getMine(Long memoryId, Long userId) {
    return memoryRepository
        .findByIdAndUserIdAndTimeCapsuleIsNullAndDeletedAtIsNull(memoryId, userId)
        .orElseThrow(() -> new ApiException(MemoryErrorCode.MEMORY_NOT_FOUND));
  }

  /**
   * 저장할 사진 순서를 정한다. 대표 사진은 display_order 0 번으로 옮기고, 나머지는 보낸 순서를 지킨다. 대표 사진 컬럼을 따로 두지 않고 0 번을 대표
   * 사진으로 본다(명세: 대표 사진은 첫 사진).
   *
   * <p>이미지 업로드 URL 발급으로 내 경로에 만든 key 만 받는다. 아무 문자열이나 받으면 다른 회원이 올린 사진의 key 를 내 아카이브에 붙여 그 사진을 볼 수
   * 있게 된다.
   */
  private List<String> arrangeImageKeys(
      Long userId, List<String> imageKeys, String representativeImageKey) {
    boolean allMine =
        imageKeys.stream().allMatch(key -> ImageKeys.isOwnedBy(ImageTarget.MEMORY, key, userId));
    boolean noDuplicate = new HashSet<>(imageKeys).size() == imageKeys.size();
    boolean representativeIncluded =
        representativeImageKey == null || imageKeys.contains(representativeImageKey);

    if (!allMine || !noDuplicate || !representativeIncluded) {
      throw new ApiException(MemoryErrorCode.INVALID_IMAGE_KEY);
    }

    List<String> arranged = new ArrayList<>(imageKeys);
    if (representativeImageKey != null) {
      arranged.remove(representativeImageKey);
      arranged.add(0, representativeImageKey);
    }
    return arranged;
  }

  /** 정한 순서를 display_order 0, 1, 2... 로 저장한다. image_url 컬럼에는 S3 key 를 저장한다. */
  private void saveImages(Memory memory, List<String> imageKeys) {
    List<MemoryImage> images = new ArrayList<>();

    for (int order = 0; order < imageKeys.size(); order++) {
      images.add(
          MemoryImage.builder()
              .memory(memory)
              .imageUrl(imageKeys.get(order))
              .displayOrder(order)
              .build());
    }

    memoryImageRepository.saveAll(images);
  }

  /**
   * 아카이브 탭 목록. 연도는 그 해의 1월 1일 ~ 12월 31일 범위로 바꿔 넘긴다. 검색어의 {@code %} · {@code _} 는 캘린더 통합 검색과 같이 글자
   * 그대로 찾는다.
   */
  public MemoryPage findMyMemories(Long userId, String keyword, Integer year, int page, int size) {
    String pattern = LikePattern.contains(keyword);
    LocalDate startDate = null;
    LocalDate endDate = null;

    if (year != null) {
      validateYear(year);
      startDate = LocalDate.of(year, 1, 1);
      endDate = LocalDate.of(year, 12, 31);
    }

    Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
    Page<Memory> memories =
        memoryRepository.findMyMemories(userId, pattern, startDate, endDate, pageable);

    return new MemoryPage(memories, findImagesByMemory(memories.getContent()));
  }

  /** 캘린더 월별 점. 아카이브 목록과 같은 추억 중 날짜가 {@code [from, to)} 인 것의 날짜다. */
  public List<LocalDate> findMyMemoryDates(Long userId, LocalDate from, LocalDate to) {
    return memoryRepository.findMyMemoryDatesBetween(userId, from, to);
  }

  /** 캘린더 날짜별 추억. 아카이브 목록과 같은 항목 형태로 만든다. */
  public List<MemorySummary> findMyMemoriesOn(Long userId, LocalDate date) {
    return summarize(memoryRepository.findMyMemoriesOn(userId, date));
  }

  /**
   * 통합 검색. 아카이브 목록과 같은 추억 중 제목에 검색어가 들어 있고 날짜가 그 해인 것을 날짜 · ID 오름차순으로 읽는다. 아카이브 목록 검색과 달리 {@code %}
   * · {@code _} 를 글자 그대로 찾는다.
   *
   * @param year null 이면 전체 기간
   */
  public List<MemorySummary> searchMyMemories(Long userId, String keyword, Integer year) {
    LocalDate from = year == null ? null : LocalDate.of(year, 1, 1);
    LocalDate to = year == null ? null : from.plusYears(1);
    return summarize(
        memoryRepository.searchMyMemories(userId, LikePattern.contains(keyword), from, to));
  }

  /** 아카이브 목록과 같은 항목 형태로 만든다. 이미지는 쿼리 한 번으로 모은다. */
  private List<MemorySummary> summarize(List<Memory> memories) {
    Map<Long, List<MemoryImage>> imagesByMemory = findImagesByMemory(memories);

    return memories.stream()
        .map(
            memory ->
                MemorySummary.of(
                    memory,
                    imagesByMemory.getOrDefault(memory.getId(), List.of()),
                    memory.getUser().getNickname(),
                    presignedUrlProvider::viewUrl))
        .toList();
  }

  /** 캘린더 탭 개수. 아카이브 목록에 검색 조건 없이 나오는 추억의 수다. */
  public long countMyMemories(Long userId) {
    return memoryRepository.countMyMemories(userId);
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

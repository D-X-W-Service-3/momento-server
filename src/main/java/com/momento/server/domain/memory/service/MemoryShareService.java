package com.momento.server.domain.memory.service;

import com.momento.server.domain.memory.entity.Memory;
import com.momento.server.domain.memory.entity.MemoryImage;
import com.momento.server.domain.memory.entity.ReceivedMemory;
import com.momento.server.domain.memory.exception.MemoryErrorCode;
import com.momento.server.domain.memory.repository.MemoryImageRepository;
import com.momento.server.domain.memory.repository.MemoryRepository;
import com.momento.server.domain.memory.repository.ReceivedMemoryRepository;
import com.momento.server.global.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 추억을 공유 링크로 보내고, 받은 사람이 저장하거나 닫는다. 저장은 복사가 아니라 받은 기록이 원본을 가리키는 것이다. */
@Service
@RequiredArgsConstructor
public class MemoryShareService {

  private final MemoryRepository memoryRepository;
  private final ReceivedMemoryRepository receivedMemoryRepository;
  private final MemoryImageRepository memoryImageRepository;
  private final Clock clock;

  /** 내 추억의 공유 토큰. 처음이면 만들고, 이미 공유했으면 같은 토큰을 준다. 상세 · 수정 · 삭제와 같이 내 아카이브만 공유할 수 있다. */
  @Transactional
  public String share(Long memoryId, Long userId) {
    return memoryRepository
        .findMineForUpdate(memoryId, userId)
        .orElseThrow(() -> new ApiException(MemoryErrorCode.MEMORY_NOT_FOUND))
        .share(MemoryShareService::newToken);
  }

  /**
   * 공유 링크로 받는다. 같은 링크를 여러 번 열어도 한 건이다. 닫았던 아카이브면 받은 아카이브로 되돌리고, 저장한 아카이브면 그대로 둔다.
   *
   * <p>없는 토큰과 삭제된 추억의 토큰은 똑같이 404 다.
   */
  @Transactional
  public void receive(String shareToken, Long userId) {
    Memory memory =
        memoryRepository
            .findByShareTokenAndDeletedAtIsNull(shareToken)
            .orElseThrow(() -> new ApiException(MemoryErrorCode.MEMORY_NOT_FOUND));
    if (memory.isOwnedBy(userId)) {
      throw new ApiException(MemoryErrorCode.CANNOT_RECEIVE_OWN_MEMORY);
    }

    int inserted =
        receivedMemoryRepository.insertIfAbsent(memory.getId(), userId, LocalDateTime.now(clock));
    if (inserted == 0) {
      receivedMemoryRepository
          .findByMemoryIdAndReceiverId(memory.getId(), userId)
          .ifPresent(ReceivedMemory::reopen);
    }
  }

  /** 받은 아카이브 조회. 받은 기록 · 원본 · 보낸 사람과 사진을 같은 시점에 읽도록 묶는다. */
  @Transactional(readOnly = true)
  public ReceivedMemories getReceived(Long userId) {
    List<ReceivedMemory> received = receivedMemoryRepository.findPending(userId);
    if (received.isEmpty()) {
      return new ReceivedMemories(List.of(), Map.of());
    }

    List<Long> memoryIds =
        received.stream().map(receivedMemory -> receivedMemory.getMemory().getId()).toList();
    Map<Long, List<MemoryImage>> imagesByMemory =
        memoryImageRepository.findByMemoryIdInOrderByDisplayOrderAsc(memoryIds).stream()
            .collect(Collectors.groupingBy(image -> image.getMemory().getId()));

    return new ReceivedMemories(received, imagesByMemory);
  }

  @Transactional
  public void save(Long receivedMemoryId, Long userId) {
    getPending(receivedMemoryId, userId).save();
  }

  @Transactional
  public void dismiss(Long receivedMemoryId, Long userId) {
    getPending(receivedMemoryId, userId).dismiss();
  }

  /** 아카이브 목록의 "공유받은 아카이브가 있어요" 배너. 받은 아카이브 조회에 나올 것이 있는지다. */
  public boolean hasReceived(Long userId) {
    return receivedMemoryRepository.existsPending(userId);
  }

  /** 받은 아카이브 조회에 나오는 것만 처리할 수 있다. 없는 · 남의 · 이미 처리한 · 원본이 삭제된 받은 아카이브는 똑같이 404 다. */
  private ReceivedMemory getPending(Long receivedMemoryId, Long userId) {
    return receivedMemoryRepository
        .findPendingById(receivedMemoryId, userId)
        .orElseThrow(() -> new ApiException(MemoryErrorCode.RECEIVED_MEMORY_NOT_FOUND));
  }

  private static String newToken() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}

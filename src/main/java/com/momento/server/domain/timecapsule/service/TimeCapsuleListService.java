package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.repository.CapsuleCount;
import com.momento.server.domain.timecapsule.repository.CapsuleInviteRepository;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.CapsuleStatusCount;
import com.momento.server.domain.timecapsule.repository.MyCapsule;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내가 속한 캡슐을 카드로 여러 개 읽는다. 목록 · 탭 홈처럼 캡슐 카드를 내려주는 조회가 여기 모인다. 캡슐 하나를 ID 로 다루는 일(생성 · 상세 · 개봉 확인)은
 * {@link TimeCapsuleService} 가 맡는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeCapsuleListService {

  private static final int OVERVIEW_SECTION_SIZE = 5;

  private final TimeCapsuleRepository timeCapsuleRepository;
  private final CapsuleMemberRepository capsuleMemberRepository;
  private final CapsuleInviteRepository capsuleInviteRepository;
  private final Clock clock;

  /**
   * 내가 속한 캡슐을 개봉일 순으로 한 페이지 읽는다. 다음 페이지가 있는지 알려고 {@code size + 1} 개를 읽고 하나를 버린다.
   *
   * @param keyword 비어 있으면 검색하지 않는다
   * @param cursorOpenAt 직전 페이지 마지막 캡슐의 개봉일. null 이면 첫 페이지
   */
  public TimeCapsulePage getMyCapsules(
      Long userId,
      CapsuleStatus status,
      String keyword,
      LocalDateTime cursorOpenAt,
      Long cursorId,
      int size) {
    String pattern = TimeCapsuleRepository.containsPattern(keyword);
    List<MyCapsule> found =
        timeCapsuleRepository.findMyCapsules(
            userId, status, pattern, cursorOpenAt, cursorId, Limit.of(size + 1));
    boolean hasNext = found.size() > size;
    List<MyCapsule> page = hasNext ? found.subList(0, size) : found;

    Map<CapsuleStatus, Long> countsByStatus =
        timeCapsuleRepository.countMyCapsulesByStatus(userId, pattern).stream()
            .collect(Collectors.toMap(CapsuleStatusCount::status, CapsuleStatusCount::count));

    return new TimeCapsulePage(summarize(page), countsByStatus, hasNext);
  }

  /**
   * 탭 홈의 네 섹션과 초대 배너 여부를 읽는다. 같은 캡슐이 "곧 만날" 과 다른 섹션에 함께 나올 수 있어서, 멤버 수 · 편지 수는 네 섹션의 캡슐을 중복 없이 모아 한
   * 번에 센다.
   */
  public TimeCapsuleOverview getOverview(Long userId) {
    Limit section = Limit.of(OVERVIEW_SECTION_SIZE);
    List<MyCapsule> upcoming = timeCapsuleRepository.findMyUnopenedCapsules(userId, section);
    List<MyCapsule> writing = timeCapsuleRepository.findMyWritingCapsules(userId, section);
    List<MyCapsule> locked =
        timeCapsuleRepository.findMyCapsules(
            userId, CapsuleStatus.LOCKED, null, null, null, section);
    List<MyCapsule> opened = timeCapsuleRepository.findMyOpenedCapsules(userId, section);

    Map<Long, MyCapsule> distinct = new LinkedHashMap<>();
    Stream.of(upcoming, writing, locked, opened)
        .flatMap(List::stream)
        .forEach(mine -> distinct.putIfAbsent(mine.capsule().getId(), mine));
    Map<Long, TimeCapsuleSummary> summaries =
        summarize(List.copyOf(distinct.values())).stream()
            .collect(Collectors.toMap(summary -> summary.capsule().getId(), summary -> summary));

    boolean hasPendingInvite =
        capsuleInviteRepository.existsPendingFor(userId, LocalDateTime.now(clock));

    return new TimeCapsuleOverview(
        hasPendingInvite,
        pick(upcoming, summaries),
        pick(writing, summaries),
        pick(locked, summaries),
        pick(opened, summaries));
  }

  private List<TimeCapsuleSummary> pick(
      List<MyCapsule> section, Map<Long, TimeCapsuleSummary> summaries) {
    return section.stream().map(mine -> summaries.get(mine.capsule().getId())).toList();
  }

  /** 카드마다 세지 않고, 캡슐 ID 를 모아 멤버 수와 편지 수를 한 번씩 센다. */
  private List<TimeCapsuleSummary> summarize(List<MyCapsule> capsules) {
    if (capsules.isEmpty()) {
      return List.of();
    }
    List<Long> ids = capsules.stream().map(mine -> mine.capsule().getId()).toList();
    Map<Long, Long> memberCounts =
        toMap(capsuleMemberRepository.countByTimeCapsuleIdInAndStatus(ids, MemberStatus.ACTIVE));
    Map<Long, Long> letterCounts =
        toMap(timeCapsuleRepository.countLettersByStatusIn(ids, LetterStatus.SUBMITTED));

    return capsules.stream()
        .map(
            mine -> {
              Long id = mine.capsule().getId();
              return new TimeCapsuleSummary(
                  mine.capsule(),
                  mine.openedAt() != null,
                  memberCounts.getOrDefault(id, 0L),
                  letterCounts.getOrDefault(id, 0L));
            })
        .toList();
  }

  private Map<Long, Long> toMap(List<CapsuleCount> counts) {
    return counts.stream().collect(Collectors.toMap(CapsuleCount::capsuleId, CapsuleCount::count));
  }
}

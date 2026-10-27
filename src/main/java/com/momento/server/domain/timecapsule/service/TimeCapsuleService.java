package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.letter.entity.LetterStatus;
import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.CapsuleSchedule;
import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.exception.CapsuleErrorCode;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import com.momento.server.global.common.exception.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeCapsuleService {

  private final TimeCapsuleRepository timeCapsuleRepository;
  private final CapsuleMemberRepository capsuleMemberRepository;
  private final Clock clock;

  /**
   * 캡슐과 생성자의 OWNER 참여 행을 만든다. {@code creator_id} 와 OWNER 행은 같은 사실을 두 곳에 담는데, 소유권 이전이 없어 둘이 어긋날 수 있는
   * 시점은 생성 순간뿐이다. 그래서 반드시 이 메서드 하나의 트랜잭션 안에서 함께 만든다.
   */
  @Transactional
  public TimeCapsule create(User creator, TimeCapsuleCreateRequest request) {
    validateVisibility(request.capsuleType(), request.visibilityType());
    CapsuleSchedule schedule = CapsuleSchedule.of(request.letterDeadlineDate(), request.openDate());
    if (!schedule.isValidOn(LocalDate.now(clock))) {
      throw new ApiException(CapsuleErrorCode.INVALID_CAPSULE_SCHEDULE);
    }

    TimeCapsule capsule =
        timeCapsuleRepository.save(
            TimeCapsule.builder()
                .creator(creator)
                .iconType(request.iconType())
                .title(request.title())
                .description(request.description())
                .capsuleType(request.capsuleType())
                .visibilityType(request.visibilityType())
                .letterDeadlineAt(schedule.letterDeadlineAt())
                .openAt(schedule.openAt())
                .build());

    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(creator)
            .role(MemberRole.OWNER)
            .joinedAt(capsule.getCreatedAt())
            .build());

    return capsule;
  }

  /**
   * 나에게 쓰는 캡슐은 생성자가 OWNER 로만 들어가 수신자가 없어 {@code RECIPIENT_AND_AUTHOR} 의 결과가 {@code ALL_MEMBERS} 와
   * 같다. 고를 의미가 없는데 허용하면 같은 캡슐이 두 값으로 저장돼 통계·필터에서 갈리므로 하나만 받는다. 조용히 바꿔 저장하지 않고 400 으로 거절해 계약을 분명히
   * 한다.
   */
  private void validateVisibility(CapsuleType capsuleType, VisibilityType visibilityType) {
    if (capsuleType == CapsuleType.SELF && visibilityType != VisibilityType.ALL_MEMBERS) {
      throw new ApiException(CapsuleErrorCode.INVALID_SELF_CAPSULE_VISIBILITY);
    }
  }

  /**
   * 참여 중인 회원에게만 상세를 보여준다. 없는 캡슐, 삭제된 캡슐, 참여하지 않았거나 나간 캡슐을 모두 같은 404 로 돌려준다 — 구분하면 순번 ID 를 대입하는 것만으로
   * 캡슐 존재 여부가 드러난다.
   */
  public TimeCapsuleDetail getDetail(Long capsuleId, Long userId) {
    TimeCapsule capsule = getActiveCapsule(capsuleId);

    CapsuleMember me = requireActiveMember(capsuleId, userId);

    long memberCount =
        capsuleMemberRepository.countByTimeCapsuleIdAndStatus(capsuleId, MemberStatus.ACTIVE);
    long letterCount =
        timeCapsuleRepository.countLettersByStatus(capsuleId, LetterStatus.SUBMITTED);

    return new TimeCapsuleDetail(capsule, me.getRole(), memberCount, letterCount);
  }

  /**
   * 열린 캡슐을 확인했다고 기록하고, 처음 확인한 일시를 돌려준다. 이미 확인한 캡슐이면 기록을 바꾸지 않고 처음 일시를 그대로 돌려준다.
   *
   * <p>멤버인지를 공개 여부보다 먼저 본다. 반대로 하면 멤버가 아닌 사람도 409 와 404 의 차이로 캡슐의 공개 여부를 알 수 있다. 공개 여부는 스케줄러가 바꿔 둔
   * {@code status} 를 믿고, 여기서 공개 시각과 현재 시각을 다시 비교하지 않는다.
   *
   * <p>격리 수준을 READ_COMMITTED 로 둔다. 같은 멤버의 요청이 동시에 오면 늦은 쪽의 조건부 UPDATE 는 0행이 되는데, REPEATABLE READ
   * 에서는 이어지는 조회가 트랜잭션 첫 조회 시점의 스냅샷을 읽어 {@code opened_at} 이 비어 보인다. READ_COMMITTED 면 먼저 커밋된 처음 일시를
   * 읽는다.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public LocalDateTime open(Long capsuleId, Long userId) {
    TimeCapsule capsule = getActiveCapsule(capsuleId);
    CapsuleMember me = requireActiveMember(capsuleId, userId);

    if (capsule.getStatus() != CapsuleStatus.OPENED) {
      throw new ApiException(CapsuleErrorCode.CAPSULE_NOT_OPENED);
    }

    capsuleMemberRepository.markOpenedIfAbsent(me.getId(), LocalDateTime.now(clock));
    return capsuleMemberRepository.findById(me.getId()).orElseThrow().getOpenedAt();
  }

  public TimeCapsule getActiveCapsule(Long capsuleId) {
    return timeCapsuleRepository
        .findByIdAndDeletedAtIsNull(capsuleId)
        .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_NOT_FOUND));
  }

  /** 호출한 서비스의 쓰기 트랜잭션에 참여해 후속 저장이 끝날 때까지 캡슐 행 잠금을 유지한다. */
  @Transactional
  public TimeCapsule getActiveCapsuleForUpdate(Long capsuleId) {
    return timeCapsuleRepository
        .findActiveByIdForUpdate(capsuleId)
        .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_NOT_FOUND));
  }

  public CapsuleMember requireActiveMember(Long capsuleId, Long userId) {
    return capsuleMemberRepository
        .findByTimeCapsuleIdAndUserIdAndStatus(capsuleId, userId, MemberStatus.ACTIVE)
        .orElseThrow(() -> new ApiException(CapsuleErrorCode.CAPSULE_NOT_FOUND));
  }
}

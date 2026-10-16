package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import com.momento.server.domain.timecapsule.entity.InviteStatus;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CapsuleInviteRepository extends JpaRepository<CapsuleInvite, Long> {

  /**
   * 재생성 시 REVOKED 처리할 "같은 슬롯"의 기존 링크를 찾는다. 슬롯은 (캡슐, 대상 역할, 지정 대상)이다 — 공유형(invitee=null)과 지정형은 서로 다른
   * 슬롯이라 섞이지 않는다. {@code inviteeId} 가 null 이면 공유형끼리만 비교한다.
   */
  @Query(
      "SELECT i FROM CapsuleInvite i "
          + "WHERE i.timeCapsule.id = :timeCapsuleId AND i.targetRole = :targetRole AND i.status = :status "
          + "AND ((:inviteeId IS NULL AND i.invitee IS NULL) OR i.invitee.id = :inviteeId)")
  Optional<CapsuleInvite> findActiveSlot(
      @Param("timeCapsuleId") Long timeCapsuleId,
      @Param("targetRole") MemberRole targetRole,
      @Param("inviteeId") Long inviteeId,
      @Param("status") InviteStatus status);

  /** 취소 대상 조회. 다른 캡슐의 초대 ID 는 없는 것으로 취급한다. */
  Optional<CapsuleInvite> findByIdAndTimeCapsuleId(Long id, Long timeCapsuleId);

  /**
   * 검증용. 서비스 로직에선 더 이상 안 쓴다(미리보기·수락·거절 모두 선점 때문에 잠금이 필요해 {@link #findByInviteTokenForUpdate} 를 쓴다).
   */
  Optional<CapsuleInvite> findByInviteToken(String inviteToken);

  /**
   * 받은 초대함 후보. 상태로 거르지 않고 최신순으로 전부 가져온다 — "지금 응답 대기 중인가"는 저장된 status 만으로 못 가린다(수락돼도 status 는 그대로고,
   * usedCount 로 소진 여부가 갈린다). 실제 필터링은 {@link CapsuleInvite#isUsable} 로 서비스에서 한다.
   *
   * <p>{@code inviter} 를 즉시 로딩한다 — 받은 목록 응답의 {@code inviterNickname} 은 트랜잭션이 끝난 뒤(Facade)에서 조립되는데,
   * {@code open-in-view: false} 라 지연 로딩된 값을 그때 건드리면 {@code LazyInitializationException} 이 난다.
   */
  @Query(
      "SELECT i FROM CapsuleInvite i JOIN FETCH i.inviter WHERE i.invitee.id = :inviteeId ORDER BY i.createdAt DESC")
  List<CapsuleInvite> findByInviteeIdOrderByCreatedAtDesc(@Param("inviteeId") Long inviteeId);

  /** 미리보기·수락·거절이 공유하는 조회 — 셋 다 "선점"이라는 쓰기를 동반해, 동시에 들어온 두 요청이 같은 링크를 함께 선점하지 않도록 잠근다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from CapsuleInvite i where i.inviteToken = :inviteToken")
  Optional<CapsuleInvite> findByInviteTokenForUpdate(@Param("inviteToken") String inviteToken);
}

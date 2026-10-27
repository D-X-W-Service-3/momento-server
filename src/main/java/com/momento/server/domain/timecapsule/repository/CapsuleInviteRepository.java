package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CapsuleInviteRepository extends JpaRepository<CapsuleInvite, Long> {

  /**
   * 내가 링크를 열고 아직 수락 · 거절하지 않은 초대가 있는지. 탭 홈의 "참여 요청받은 타임캡슐이 있어요" 배너에 쓴다.
   *
   * <p>나에게 고정된 ACTIVE 초대 중 만료되지 않았고 캡슐이 삭제되지 않은 것을 찾는다. 만료는 저장된 상태가 아니라 {@code expiresAt} 으로 판단한다.
   * 이미 그 캡슐의 ACTIVE 멤버면 대기 중이 아니므로 뺀다.
   */
  @Query(
      "select case when count(i) > 0 then true else false end"
          + " from CapsuleInvite i"
          + " where i.invitee.id = :userId"
          + " and i.status = com.momento.server.domain.timecapsule.entity.InviteStatus.ACTIVE"
          + " and (i.expiresAt is null or i.expiresAt > :now)"
          + " and i.timeCapsule.deletedAt is null"
          + " and not exists (select cm.id from CapsuleMember cm"
          + " where cm.timeCapsule = i.timeCapsule and cm.user.id = :userId"
          + " and cm.status = com.momento.server.domain.timecapsule.entity.MemberStatus.ACTIVE)")
  boolean existsPendingFor(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}

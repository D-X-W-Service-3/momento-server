package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.CapsuleInvitee;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CapsuleInviteeRepository extends JpaRepository<CapsuleInvitee, Long> {

  /**
   * 내가 링크를 열고 아직 수락 · 거절하지 않은 초대가 있는지. 탭 홈의 "참여 요청받은 타임캡슐이 있어요" 배너에 쓴다.
   *
   * <p>내 PENDING 행 중, 링크가 ACTIVE 이고 만료되지 않았으며 캡슐이 삭제되지 않은 것을 찾는다. 만료는 저장된 상태가 아니라 {@code expiresAt}
   * 으로 판단한다. 수락은 캡슐이 WRITING 일 때만 되므로, 잠기거나 열린 캡슐의 초대는 눌러도 참여할 수 없어 뺀다. 이미 그 캡슐의 ACTIVE 멤버여도 대기 중이
   * 아니므로 뺀다.
   */
  @Query(
      "select case when count(v) > 0 then true else false end"
          + " from CapsuleInvitee v join v.capsuleInvite i join i.timeCapsule c"
          + " where v.user.id = :userId"
          + " and v.status = com.momento.server.domain.timecapsule.entity.InviteeStatus.PENDING"
          + " and i.status = com.momento.server.domain.timecapsule.entity.InviteStatus.ACTIVE"
          + " and (i.expiresAt is null or i.expiresAt > :now)"
          + " and c.deletedAt is null"
          + " and c.status = com.momento.server.domain.timecapsule.entity.CapsuleStatus.WRITING"
          + " and not exists (select cm.id from CapsuleMember cm"
          + " where cm.timeCapsule = c and cm.user.id = :userId"
          + " and cm.status = com.momento.server.domain.timecapsule.entity.MemberStatus.ACTIVE)")
  boolean existsPendingFor(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}

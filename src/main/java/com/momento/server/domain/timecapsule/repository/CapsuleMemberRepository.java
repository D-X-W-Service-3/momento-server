package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CapsuleMemberRepository extends JpaRepository<CapsuleMember, Long> {

  Optional<CapsuleMember> findByTimeCapsuleIdAndUserIdAndStatus(
      Long timeCapsuleId, Long userId, MemberStatus status);

  long countByTimeCapsuleIdAndStatus(Long timeCapsuleId, MemberStatus status);

  /** {@link #countByTimeCapsuleIdAndStatus} 를 캡슐 여러 개에 한 번에 센다. 해당 멤버가 없는 캡슐은 결과에 없다. */
  @Query(
      "select new com.momento.server.domain.timecapsule.repository.CapsuleCount("
          + "m.timeCapsule.id, count(m))"
          + " from CapsuleMember m"
          + " where m.timeCapsule.id in :capsuleIds and m.status = :status"
          + " group by m.timeCapsule.id")
  List<CapsuleCount> countByTimeCapsuleIdInAndStatus(
      @Param("capsuleIds") Collection<Long> capsuleIds, @Param("status") MemberStatus status);

  /**
   * 멤버가 캡슐을 확인한 시각을 기록한다. 이 멤버의 {@code opened_at} 이 비어 있을 때만 쓰고, 전에 확인해서 값이 있으면 그대로 둔다. 그래서 여러 번
   * 확인하거나 요청이 동시에 들어와도 처음 확인한 시각이 남는다. 영속성 컨텍스트를 비워, 같은 트랜잭션에서 다시 읽으면 기록된 값이 보이게 한다.
   *
   * @return 실제로 기록한 행 수. 처음 확인이면 1, 전에 확인한 멤버면 0
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      "update CapsuleMember m set m.openedAt = :now where m.id = :memberId and m.openedAt is null")
  int markOpenedIfAbsent(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}

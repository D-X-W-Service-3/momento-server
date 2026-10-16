package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CapsuleMemberRepository extends JpaRepository<CapsuleMember, Long> {

  Optional<CapsuleMember> findByTimeCapsuleIdAndUserIdAndStatus(
      Long timeCapsuleId, Long userId, MemberStatus status);

  /** 상태 무관 조회. 초대 수락 시 나갔거나 제외됐던 회원인지 확인해 재활성화할지 새로 만들지 정하는 데 쓴다. */
  Optional<CapsuleMember> findByTimeCapsuleIdAndUserId(Long timeCapsuleId, Long userId);

  long countByTimeCapsuleIdAndStatus(Long timeCapsuleId, MemberStatus status);

  /** 초대 미리보기의 참여자·수신자 미리보기 인원 수(역할별). */
  long countByTimeCapsuleIdAndRoleAndStatus(
      Long timeCapsuleId, MemberRole role, MemberStatus status);

  /**
   * 초대 미리보기의 참여자·수신자 아바타 목록. {@code pageable} 로 미리보기 개수를 제한한다(예: 상위 5명). {@code user} 를 즉시 로딩한다 —
   * 닉네임·프로필사진은 트랜잭션이 끝난 뒤(Facade)에서 조립되는데, {@code open-in-view: false} 라 지연 로딩된 값을 그때 건드리면 {@code
   * LazyInitializationException} 이 난다.
   */
  @Query(
      "SELECT cm FROM CapsuleMember cm JOIN FETCH cm.user "
          + "WHERE cm.timeCapsule.id = :timeCapsuleId AND cm.role = :role AND cm.status = :status "
          + "ORDER BY cm.joinedAt ASC")
  List<CapsuleMember> findByTimeCapsuleIdAndRoleAndStatusOrderByJoinedAtAsc(
      @Param("timeCapsuleId") Long timeCapsuleId,
      @Param("role") MemberRole role,
      @Param("status") MemberStatus status,
      Pageable pageable);
}

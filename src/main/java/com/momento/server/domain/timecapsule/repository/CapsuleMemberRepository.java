package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapsuleMemberRepository extends JpaRepository<CapsuleMember, Long> {

  Optional<CapsuleMember> findByTimeCapsuleIdAndUserIdAndStatus(
      Long timeCapsuleId, Long userId, MemberStatus status);

  long countByTimeCapsuleIdAndStatus(Long timeCapsuleId, MemberStatus status);
}

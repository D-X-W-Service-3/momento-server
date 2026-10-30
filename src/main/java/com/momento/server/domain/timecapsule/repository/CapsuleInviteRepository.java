package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import com.momento.server.domain.timecapsule.entity.InviteStatus;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CapsuleInviteRepository extends JpaRepository<CapsuleInvite, Long> {

  /** 캡슐 · 역할마다 활성 링크는 하나다. 발급은 이 링크를 그대로 돌려주고, 재생성은 이 링크를 REVOKED 한다. */
  Optional<CapsuleInvite> findByTimeCapsuleIdAndTargetRoleAndStatus(
      Long timeCapsuleId, MemberRole targetRole, InviteStatus status);

  Optional<CapsuleInvite> findByInviteToken(String inviteToken);

  /**
   * 링크가 가리키는 캡슐 ID 만 읽는다. 수락은 캡슐 행을 먼저 잠그고 링크 행을 잠근다(재생성과 같은 순서라 교착이 생기지 않는다). 링크 엔티티를 먼저 읽어 두면 잠근
   * 뒤 다시 읽어도 영속성 컨텍스트에 남은 옛 값이 나오므로, 잠그기 전에는 ID 만 본다.
   */
  @Query("select i.timeCapsule.id from CapsuleInvite i where i.inviteToken = :inviteToken")
  Optional<Long> findCapsuleIdByInviteToken(@Param("inviteToken") String inviteToken);

  /**
   * 미리보기 · 수락 · 거절이 링크 행을 잠그고 읽는다. 같은 회원의 요청이 겹쳐도 응답 기록({@code capsule_invitees})을 확인하고 만드는 일이 한 번에
   * 하나씩 일어나고, 그사이 재생성으로 무효가 된 링크를 옛 상태로 보지 않는다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from CapsuleInvite i where i.inviteToken = :inviteToken")
  Optional<CapsuleInvite> findByInviteTokenForUpdate(@Param("inviteToken") String inviteToken);
}

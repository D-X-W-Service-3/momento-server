package com.momento.server.domain.push.repository;

import com.momento.server.domain.push.entity.PushSubscription;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

  Optional<PushSubscription> findByEndpoint(String endpoint);

  @Query("select s from PushSubscription s where s.user.id in :userIds")
  List<PushSubscription> findAllByUserIds(@Param("userIds") Collection<Long> userIds);

  /** 푸시 서비스가 사라졌다고 응답한 구독을 지운다(404 · 410). */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from PushSubscription s where s.endpoint in :endpoints")
  int deleteAllByEndpoints(@Param("endpoints") Collection<String> endpoints);

  /** 그 회원의 구독일 때만 지운다. 남의 endpoint 를 알아도 지울 수 없다. */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from PushSubscription s where s.endpoint = :endpoint and s.user.id = :userId")
  int deleteByEndpointAndUserId(@Param("endpoint") String endpoint, @Param("userId") Long userId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("delete from PushSubscription s where s.user.id = :userId")
  int deleteAllByUserId(@Param("userId") Long userId);
}

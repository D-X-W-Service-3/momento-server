package com.momento.server.domain.notification.repository;

import com.momento.server.domain.notification.entity.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

  /** id DESC(=최신순) keyset 페이지네이션. cursor 가 없으면 첫 페이지부터 조회한다. */
  @Query(
      "SELECT n FROM Notification n "
          + "WHERE n.user.id = :userId AND (:cursor IS NULL OR n.id < :cursor) "
          + "ORDER BY n.id DESC")
  List<Notification> findPage(
      @Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);

  long countByUserIdAndReadFalse(Long userId);

  /** 소유권 검증까지 한 번에 한다 — 타 회원 알림이면 존재하지 않는 것으로 취급한다. */
  Optional<Notification> findByIdAndUserId(Long id, Long userId);
}

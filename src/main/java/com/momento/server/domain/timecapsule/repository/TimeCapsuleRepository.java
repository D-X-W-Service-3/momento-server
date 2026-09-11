package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeCapsuleRepository extends JpaRepository<TimeCapsule, Long> {}

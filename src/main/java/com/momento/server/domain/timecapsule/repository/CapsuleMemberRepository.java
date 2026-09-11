package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapsuleMemberRepository extends JpaRepository<CapsuleMember, Long> {}

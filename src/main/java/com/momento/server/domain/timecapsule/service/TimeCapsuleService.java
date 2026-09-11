package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.dto.request.TimeCapsuleCreateRequest;
import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.repository.CapsuleMemberRepository;
import com.momento.server.domain.timecapsule.repository.TimeCapsuleRepository;
import com.momento.server.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeCapsuleService {

  private final TimeCapsuleRepository timeCapsuleRepository;
  private final CapsuleMemberRepository capsuleMemberRepository;

  /**
   * 캡슐과 생성자의 OWNER 참여 행을 만든다. {@code creator_id} 와 OWNER 행은 같은 사실을 두 곳에 담는데, 소유권 이전이 없어 둘이 어긋날 수 있는
   * 시점은 생성 순간뿐이다. 그래서 반드시 이 메서드 하나의 트랜잭션 안에서 함께 만든다.
   */
  @Transactional
  public TimeCapsule create(User creator, TimeCapsuleCreateRequest request) {
    TimeCapsule capsule =
        timeCapsuleRepository.save(
            TimeCapsule.builder()
                .creator(creator)
                .title(request.title())
                .description(request.description())
                .capsuleType(request.capsuleType())
                .visibilityType(request.visibilityType())
                .openAt(request.openAt())
                .build());

    capsuleMemberRepository.save(
        CapsuleMember.builder()
            .timeCapsule(capsule)
            .user(creator)
            .role(MemberRole.OWNER)
            .joinedAt(capsule.getCreatedAt())
            .build());

    return capsule;
  }
}

package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import java.util.List;

/**
 * 멤버 목록 화면에 필요한 값.
 *
 * @param role 조회한 역할. {@code PARTICIPANT} 는 방장을 포함한다
 * @param members 방장이 맨 앞이고 나머지는 참여한 순서다. 회원을 함께 읽어 두었다
 */
public record CapsuleMemberList(MemberRole role, List<CapsuleMember> members, boolean canManage) {}

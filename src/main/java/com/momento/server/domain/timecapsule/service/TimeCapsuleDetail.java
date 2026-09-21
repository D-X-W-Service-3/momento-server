package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;

/**
 * 캡슐 상세 조회의 서비스 결과. 서비스는 웹 응답 DTO 대신 이 결과를 돌려주고, 응답 모양으로 바꾸는 일은 Facade 가 맡는다. 응답 필드가 바뀌어도 서비스는 그대로
 * 둘 수 있다.
 */
public record TimeCapsuleDetail(
    TimeCapsule capsule, MemberRole myRole, long memberCount, long letterCount) {}

package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.entity.CapsuleInvite;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;

/**
 * 초대 미리보기의 서비스 결과. 캡슐 요약은 상세 화면({@link TimeCapsuleDetail})과 같은 기준으로 센다.
 *
 * @param alreadyJoined 로그인한 회원이 이미 이 캡슐의 ACTIVE 멤버인지
 * @param memberCount 참여 중인 인원 수 (방장 포함)
 * @param letterCount 제출됐고 삭제되지 않은 편지 수
 * @param participants 방장과 참여자
 * @param recipients 수신자
 */
public record CapsuleInvitePreview(
    CapsuleInvite invite,
    TimeCapsule capsule,
    boolean alreadyJoined,
    long memberCount,
    long letterCount,
    TimeCapsuleDetail.MemberGroup participants,
    TimeCapsuleDetail.MemberGroup recipients) {}

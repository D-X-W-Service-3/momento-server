package com.momento.server.domain.timecapsule.repository;

import com.momento.server.domain.timecapsule.entity.MemberRole;
import java.time.LocalDateTime;

/** 받은 초대 목록 한 줄. 내가 열고 아직 응답하지 않은 초대 링크다. */
public record ReceivedInvite(
    String inviteToken,
    MemberRole targetRole,
    String inviterNickname,
    Long capsuleId,
    String capsuleTitle,
    LocalDateTime openAt) {}

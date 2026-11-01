package com.momento.server.domain.timecapsule.entity;

/** 초대 링크를 연 회원의 응답 상태. 링크를 열면 PENDING 이고, 수락 · 거절로 정리된다. */
public enum InviteeStatus {
  PENDING,
  ACCEPTED,
  DECLINED
}

package com.momento.server.domain.timecapsule.entity;

/** 타임캡슐이 열린 뒤 편지를 누가 볼 수 있는지. 값 이름이 곧 "볼 수 있는 사람" 이다. */
public enum VisibilityType {
  /** 수신자만 본다. 편지를 쓴 사람은 OWNER 라도 자기 편지를 다시 보지 못한다. */
  RECIPIENT_ONLY,
  /** 수신자는 모든 편지를, 그 밖의 참여자는 자기가 쓴 편지만 본다. */
  RECIPIENT_AND_AUTHOR,
  /** 참여자 전원이 모든 편지를 본다. */
  ALL_MEMBERS
}

package com.momento.server.domain.memory.entity;

/** 공유 링크로 받은 아카이브의 상태. */
public enum ReceivedMemoryStatus {
  /** 링크를 열고 아직 저장 · 닫기를 하지 않았다. 받은 아카이브 조회에 나온다. */
  PENDING,
  /** 내 아카이브에 저장했다. */
  SAVED,
  /** 닫았다. 다시 보이지 않는다. */
  DISMISSED
}

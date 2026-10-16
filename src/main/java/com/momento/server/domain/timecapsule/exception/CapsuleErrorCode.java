package com.momento.server.domain.timecapsule.exception;

import com.momento.server.global.common.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CapsuleErrorCode implements ErrorCode {
  /** 없는 캡슐, 삭제된 캡슐, 참여하지 않은 캡슐을 구분하지 않는다. 구분하면 순번 ID 를 대입하는 것만으로 캡슐 존재 여부가 드러난다. */
  CAPSULE_NOT_FOUND("타임캡슐을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

  /** 나에게 쓰는 캡슐은 참여자가 생성자 한 명뿐이라 공개 범위를 고를 여지가 없다. */
  INVALID_SELF_CAPSULE_VISIBILITY("나에게 쓰는 캡슐의 공개 범위는 전체 공개여야 합니다.", HttpStatus.BAD_REQUEST),

  /** 초대 링크 발급·재생성·취소는 OWNER만 할 수 있다. */
  CAPSULE_OWNER_ONLY("방장만 할 수 있는 작업입니다.", HttpStatus.FORBIDDEN),

  /**
   * 편지 작성이 끝난(LOCKED) 이후나 이미 공개된(OPENED) 캡슐에서는 초대 발급·재생성도, 수락도 막는다. 초대 자체(링크)는 아직 유효해도 캡슐 쪽 상태가 막아서
   * 거절하는 것이라 {@code INVITE_NOT_FOUND} 와는 다른 코드다.
   */
  CAPSULE_NOT_EDITABLE("캡슐이 잠긴 이후에는 진행할 수 없는 작업입니다.", HttpStatus.CONFLICT),

  /** {@code targetRole} 로 OWNER 를 보냈거나, 캡슐 유형(SELF/FRIEND/GROUP)에서 허용하지 않는 역할이다. */
  INVALID_INVITE_ROLE("이 캡슐에서는 해당 역할로 초대할 수 없습니다.", HttpStatus.BAD_REQUEST),

  /**
   * 다음을 모두 같은 코드로 묶는다 — 존재하지 않는 토큰, 취소·만료된 링크, 다른 회원이 이미 선점한 링크. 세분화하면 "이미 남이 선점했다" 같은 정보가 새어나간다.
   * 없는 토큰과 구분할 이유가 없어 전부 404 다.
   */
  INVITE_NOT_FOUND("유효하지 않은 초대 링크입니다.", HttpStatus.NOT_FOUND),

  /** 이미 ACTIVE 상태로 참여 중인 회원이 같은 캡슐 초대를 다시 수락한 경우. */
  ALREADY_CAPSULE_MEMBER("이미 참여 중인 타임캡슐입니다.", HttpStatus.CONFLICT),
  ;

  private final String message;
  private final HttpStatus status;
}

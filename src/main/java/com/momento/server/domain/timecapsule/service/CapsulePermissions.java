package com.momento.server.domain.timecapsule.service;

/**
 * 상세 화면에서 로그인한 멤버가 할 수 있는 동작. 각 값은 해당 API 가 거절하는 조건과 같은 판정으로 계산한다. 그래서 버튼이 보이면 눌러도 409 가 나지 않는다.
 *
 * @param canWriteLetter 편지를 쓸 수 있고 아직 이 캡슐에 내 편지가 없다
 * @param canInvite 참여자 · 수신자를 초대할 수 있다 (방장, WRITING)
 * @param canManage 캡슐을 고칠 수 있다 (방장, WRITING)
 * @param canDelete 캡슐을 지울 수 있다 (방장, WRITING, 삭제되지 않은 편지 0통)
 * @param canViewLetters 편지함에 들어갈 수 있다 (OPENED)
 */
public record CapsulePermissions(
    boolean canWriteLetter,
    boolean canInvite,
    boolean canManage,
    boolean canDelete,
    boolean canViewLetters) {}

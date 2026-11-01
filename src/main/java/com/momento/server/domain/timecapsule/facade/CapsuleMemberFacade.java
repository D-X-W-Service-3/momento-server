package com.momento.server.domain.timecapsule.facade;

import com.momento.server.domain.timecapsule.dto.response.CapsuleMemberListResponse;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.service.CapsuleMemberList;
import com.momento.server.domain.timecapsule.service.CapsuleMemberService;
import com.momento.server.global.common.code.GlobalErrorCode;
import com.momento.server.global.common.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CapsuleMemberFacade {

  private final CapsuleMemberService capsuleMemberService;

  /**
   * {@code role} 은 필수지만 {@code @RequestParam} 을 필수로 두지 않고 여기서 확인한다. 필수로 두면 빠졌을 때 처리기가 없는 {@code
   * MissingServletRequestParameterException} 이 나서 500 이 된다. 방장은 참여자 목록에 들어 있어 {@code OWNER} 로는 따로
   * 조회하지 않는다.
   */
  public CapsuleMemberListResponse getMembers(Long capsuleId, Long userId, MemberRole role) {
    if (role == null || role == MemberRole.OWNER) {
      throw new ApiException(GlobalErrorCode.INVALID_INPUT_VALUE);
    }
    CapsuleMemberList list = capsuleMemberService.getMembers(capsuleId, userId, role);

    return CapsuleMemberListResponse.of(list.role(), list.members(), list.canManage());
  }

  public void remove(Long capsuleId, Long userId, Long memberId) {
    capsuleMemberService.remove(capsuleId, userId, memberId);
  }
}

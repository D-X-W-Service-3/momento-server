package com.momento.server.domain.timecapsule.dto.response;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "캡슐 멤버 목록 응답")
public record CapsuleMemberListResponse(
    @Schema(description = "조회한 역할. PARTICIPANT 는 방장을 포함한다.", example = "PARTICIPANT")
        MemberRole role,
    @Schema(description = "인원 수", example = "8") int count,
    @Schema(description = "초대 · 내보내기를 할 수 있다 (방장, WRITING). 상세의 canManage 와 같은 판정이다.")
        boolean canManage,
    @Schema(description = "멤버 목록. 방장이 맨 앞이고, 나머지는 참여한 순서다.") List<Member> members) {

  /**
   * @param members 정렬된 ACTIVE 멤버. 회원을 함께 읽어 둔 상태여야 한다
   */
  public static CapsuleMemberListResponse of(
      MemberRole role, List<CapsuleMember> members, boolean canManage) {
    return new CapsuleMemberListResponse(
        role, members.size(), canManage, members.stream().map(Member::from).toList());
  }

  @Schema(description = "멤버 한 명")
  public record Member(
      @Schema(description = "캡슐 멤버 ID. 내보내기에 쓴다.", example = "101") Long memberId,
      @Schema(description = "회원 ID", example = "1") Long userId,
      @Schema(description = "닉네임", example = "유빈") String nickname,
      @Schema(description = "프로필 이미지 URL", nullable = true) String profileImageUrl,
      @Schema(description = "방장 여부. \"(관리자)\" 로 표시하고 내보낼 수 없다.") boolean isOwner) {

    static Member from(CapsuleMember member) {
      User user = member.getUser();
      return new Member(
          member.getId(),
          user.getId(),
          user.getNickname(),
          user.getProfileImageUrl(),
          member.isOwner());
    }
  }
}

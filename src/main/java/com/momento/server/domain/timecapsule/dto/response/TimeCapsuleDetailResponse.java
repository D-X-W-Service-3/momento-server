package com.momento.server.domain.timecapsule.dto.response;

import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.entity.VisibilityType;
import com.momento.server.domain.timecapsule.repository.MemberPreview;
import com.momento.server.domain.timecapsule.service.CapsulePermissions;
import com.momento.server.domain.timecapsule.service.TimeCapsuleDetail;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "타임캡슐 상세 응답")
public record TimeCapsuleDetailResponse(
    @Schema(description = "타임캡슐 ID", example = "21") Long capsuleId,
    @Schema(description = "대표 아이콘", example = "ICE_CREAM") String iconType,
    @Schema(description = "캡슐 이름", example = "2024 우리, 다시 만나는 날") String title,
    @Schema(description = "캡슐 설명", nullable = true) String description,
    @Schema(description = "캡슐 유형", example = "GROUP") CapsuleType capsuleType,
    @Schema(description = "편지 공개 범위", example = "ALL_MEMBERS") VisibilityType visibilityType,
    @Schema(description = "캡슐 상태", example = "WRITING") CapsuleStatus status,
    @Schema(description = "내가 개봉 화면을 확인했는지") boolean openedByMe,
    @Schema(description = "편지 수정 마감 일시", example = "2027-12-01T23:59:59")
        LocalDateTime letterDeadlineAt,
    @Schema(description = "개봉 일시", example = "2027-12-12T00:00:00") LocalDateTime openAt,
    @Schema(description = "오늘부터 개봉일까지 남은 일수. 당일 0, 지나면 음수다.", example = "136") int dDay,
    @Schema(description = "참여 중인 인원 수. 방장을 포함한다.", example = "24") long memberCount,
    @Schema(description = "제출된 편지 수. 임시 저장과 삭제된 편지는 세지 않는다.", example = "13") long letterCount,
    @Schema(description = "이 캡슐에서 내 역할", example = "OWNER") MemberRole myRole,
    @Schema(description = "참여자 미리보기. 방장을 포함한다.") Members participants,
    @Schema(description = "수신자 미리보기") Members recipients,
    @Schema(description = "로그인한 멤버가 할 수 있는 동작") Permissions permissions) {

  /**
   * @param today 서버 시간대의 오늘. 호출하는 쪽이 {@code LocalDate.now(clock)} 으로 넘긴다
   */
  public static TimeCapsuleDetailResponse of(TimeCapsuleDetail detail, LocalDate today) {
    TimeCapsule capsule = detail.capsule();
    return new TimeCapsuleDetailResponse(
        capsule.getId(),
        capsule.getIconType(),
        capsule.getTitle(),
        capsule.getDescription(),
        capsule.getCapsuleType(),
        capsule.getVisibilityType(),
        capsule.getStatus(),
        detail.me().getOpenedAt() != null,
        capsule.getLetterDeadlineAt(),
        capsule.getOpenAt(),
        TimeCapsuleCardResponse.daysUntil(today, capsule.getOpenAt()),
        detail.memberCount(),
        detail.letterCount(),
        detail.me().getRole(),
        Members.from(detail.participants()),
        Members.from(detail.recipients()),
        Permissions.from(detail.permissions()));
  }

  @Schema(description = "역할별 멤버 미리보기")
  public record Members(
      @Schema(description = "전체 인원 수", example = "12") long count,
      @Schema(description = "앞에서부터 최대 5명. 방장이 맨 앞이고, 나머지는 참여한 순서다.") List<Preview> previews) {

    static Members from(TimeCapsuleDetail.MemberGroup group) {
      return new Members(group.count(), group.previews().stream().map(Preview::from).toList());
    }
  }

  @Schema(description = "멤버 미리보기 한 명")
  public record Preview(
      @Schema(description = "회원 ID", example = "1") Long userId,
      @Schema(description = "닉네임", example = "유빈") String nickname,
      @Schema(description = "프로필 이미지 URL", nullable = true) String profileImageUrl) {

    static Preview from(MemberPreview preview) {
      return new Preview(preview.userId(), preview.nickname(), preview.profileImageUrl());
    }
  }

  @Schema(description = "로그인한 멤버가 할 수 있는 동작. 각 API 가 거절하는 조건과 같은 판정이다.")
  public record Permissions(
      @Schema(description = "편지를 쓸 수 있고 아직 이 캡슐에 내 편지가 없다 (\"나도 편지 작성하기\")") boolean canWriteLetter,
      @Schema(description = "참여자 · 수신자를 초대할 수 있다 (방장, WRITING)") boolean canInvite,
      @Schema(description = "캡슐을 고칠 수 있다 (방장, WRITING)") boolean canManage,
      @Schema(description = "캡슐을 지울 수 있다 (방장, WRITING, 편지 0통 — 임시 저장 포함)") boolean canDelete,
      @Schema(description = "편지함에 들어갈 수 있다 (OPENED)") boolean canViewLetters) {

    static Permissions from(CapsulePermissions permissions) {
      return new Permissions(
          permissions.canWriteLetter(),
          permissions.canInvite(),
          permissions.canManage(),
          permissions.canDelete(),
          permissions.canViewLetters());
    }
  }
}

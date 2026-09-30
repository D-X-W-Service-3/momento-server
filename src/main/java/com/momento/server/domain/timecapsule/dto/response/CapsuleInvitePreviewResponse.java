package com.momento.server.domain.timecapsule.dto.response;

import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.timecapsule.entity.CapsuleType;
import com.momento.server.domain.timecapsule.entity.MemberRole;
import com.momento.server.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 초대 링크로 들어온 사용자에게 보여줄 캡슐 초대 화면 응답. 편지 내용은 없지만, 참여자·수신자 닉네임·프로필사진은 미리보기로 포함한다(디자인이 그렇게 바뀌었다 — 최초
 * 설계였던 "최소 정보만"에서 확장됨).
 *
 * <p>{@code capsule.iconType} 은 아직 {@code TimeCapsule} 에 필드가 없어 이번엔 빠졌다 — 캡슐 생성 쪽에서 먼저 추가돼야 한다.
 */
@Schema(description = "초대 미리보기 응답")
public record CapsuleInvitePreviewResponse(
    @Schema(description = "초대받은 역할", example = "PARTICIPANT") MemberRole targetRole,
    @Schema(description = "이미 이 캡슐의 멤버인지. true 면 프론트가 캡슐 상세로 보낸다", example = "false")
        boolean alreadyJoined,
    @Schema(description = "캡슐 정보") CapsuleSummary capsule) {

  @Schema(description = "초대 미리보기용 캡슐 요약")
  public record CapsuleSummary(
      @Schema(description = "캡슐 ID", example = "21") Long capsuleId,
      @Schema(description = "캡슐 이름", example = "2024 우리, 다시 만나는 날") String title,
      @Schema(description = "캡슐 설명", example = "1년 동안 서로에게 응원과 사랑을 보냈던 우리의 소중한 타임캡슐")
          String description,
      @Schema(description = "캡슐 유형", example = "GROUP") CapsuleType capsuleType,
      @Schema(description = "캡슐 상태", example = "WRITING") CapsuleStatus status,
      @Schema(description = "개봉 일시", example = "2027-12-12T00:00:00") LocalDateTime openAt,
      @Schema(description = "참여 인원 수. OWNER 포함", example = "5") long memberCount,
      @Schema(description = "담긴 편지 수", example = "3") long letterCount,
      @Schema(description = "참여자 미리보기") MemberPreview participants,
      @Schema(description = "수신자 미리보기") MemberPreview recipients) {}

  @Schema(description = "역할별 참여 인원 미리보기")
  public record MemberPreview(
      @Schema(description = "해당 역할 전체 인원 수", example = "12") long count,
      @Schema(description = "아바타로 보여줄 상위 인원(최대 5명)") List<MemberPreviewItem> previews) {}

  @Schema(description = "미리보기 인원 한 명")
  public record MemberPreviewItem(
      @Schema(description = "회원 ID", example = "1") Long userId,
      @Schema(description = "닉네임", example = "유빈") String nickname,
      @Schema(
              description = "프로필 이미지 URL",
              example = "https://k.kakaocdn.net/dn/profile.jpg",
              nullable = true)
          String profileImageUrl) {

    public static MemberPreviewItem from(User user) {
      return new MemberPreviewItem(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
  }
}

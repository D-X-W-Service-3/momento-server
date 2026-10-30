package com.momento.server.domain.user.dto.response;

import com.momento.server.domain.timecapsule.entity.CapsuleStatus;
import com.momento.server.domain.user.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Map;

@Schema(description = "내 정보 조회 응답")
public record MyProfileResponse(
    @Schema(description = "회원 ID", example = "1") Long userId,
    @Schema(description = "닉네임", example = "상래") String nickname,
    @Schema(description = "프로필 이미지 URL. 지금은 카카오 프로필 이미지다.", nullable = true) String profileImageUrl,
    @Schema(description = "이메일", example = "sangrae@example.com", nullable = true) String email,
    @Schema(description = "생일", example = "2001-03-14", nullable = true) LocalDate birthday,
    @Schema(description = "전화번호", example = "010-1234-5678", nullable = true) String phoneNumber,
    @Schema(description = "활동 요약. 조회할 때 센다.") Activity activity) {

  public static MyProfileResponse of(User user, Map<CapsuleStatus, Long> capsulesByStatus) {
    return new MyProfileResponse(
        user.getId(),
        user.getNickname(),
        user.getProfileImageUrl(),
        user.getEmail(),
        user.getBirthday(),
        user.getPhoneNumber(),
        Activity.of(capsulesByStatus));
  }

  /** 캡슐 기준은 타임캡슐 목록 조회와 같다(내가 속한, 삭제되지 않은 캡슐). */
  @Schema(description = "활동 요약")
  public record Activity(
      @Schema(description = "전체 캡슐 수", example = "12") long totalCapsuleCount,
      @Schema(description = "공개 전 캡슐 수 (WRITING + LOCKED)", example = "9")
          long beforeOpenCapsuleCount,
      @Schema(description = "공개된 캡슐 수", example = "3") long openedCapsuleCount) {

    /** 캡슐이 없는 상태는 0 으로 센다. */
    public static Activity of(Map<CapsuleStatus, Long> capsulesByStatus) {
      long beforeOpen =
          capsulesByStatus.getOrDefault(CapsuleStatus.WRITING, 0L)
              + capsulesByStatus.getOrDefault(CapsuleStatus.LOCKED, 0L);
      long opened = capsulesByStatus.getOrDefault(CapsuleStatus.OPENED, 0L);
      return new Activity(beforeOpen + opened, beforeOpen, opened);
    }
  }
}

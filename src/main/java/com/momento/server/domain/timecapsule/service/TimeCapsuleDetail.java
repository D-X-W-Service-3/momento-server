package com.momento.server.domain.timecapsule.service;

import com.momento.server.domain.timecapsule.entity.CapsuleMember;
import com.momento.server.domain.timecapsule.entity.TimeCapsule;
import com.momento.server.domain.timecapsule.repository.MemberPreview;
import java.util.List;

/**
 * 캡슐 상세 조회의 서비스 결과. 서비스는 웹 응답 DTO 대신 이 결과를 돌려주고, 응답 모양으로 바꾸는 일은 Facade 가 맡는다. 응답 필드가 바뀌어도 서비스는 그대로
 * 둘 수 있다.
 *
 * @param me 로그인한 회원의 멤버 행. 역할과 개봉 확인 시각을 읽는다
 * @param letterCount 제출됐고 삭제되지 않은 편지 수 (카드와 같은 기준)
 * @param participants 방장과 참여자
 * @param recipients 수신자
 */
public record TimeCapsuleDetail(
    TimeCapsule capsule,
    CapsuleMember me,
    long memberCount,
    long letterCount,
    MemberGroup participants,
    MemberGroup recipients,
    CapsulePermissions permissions) {

  /** 역할 묶음 하나의 전체 인원과 앞에서부터 몇 명의 미리보기. */
  public record MemberGroup(long count, List<MemberPreview> previews) {}
}

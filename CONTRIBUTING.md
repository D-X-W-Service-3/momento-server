# 협업 규칙 (Contributing)

Momento 백엔드 팀의 Git 협업 컨벤션입니다.

## 커밋 메시지 컨벤션

```
<type>: <제목>

<본문 (선택)>

관련 이슈: #이슈번호
```

- **제목**: 50자 이내, 명령형(`추가한다`가 아니라 `추가`), 끝에 마침표 없음
- **본문**: 어떻게(how)보다 **왜(why)** 를 설명 (선택)
- 하나의 커밋에는 하나의 논리적 변경만 담는다

### type 목록

| type | 설명 |
|---|---|
| `init` | 프로젝트 초기 세팅 |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `build` | 빌드 관련 파일 수정, 모듈 설치/삭제 |
| `chore` | 그 외 자잘한 수정 |
| `ci` | CI 설정 수정 |
| `docs` | 문서 수정 (README 등) |
| `style` | 코드 스타일/포맷 (동작 변화 없음) |
| `refactor` | 코드 리팩터링 (기능 변화 없음) |
| `test` | 테스트 코드 추가/수정 |
| `perf` | 성능 개선 |

예시
```
feat: 카카오 로그인 성공 시 JWT 발급 추가

신규 유저는 저장 후 토큰을 발급하고, 프론트 콜백 URL로 리다이렉트한다.

관련 이슈: #12
```

### 커밋 템플릿 등록 (선택)

`.gitmessage.txt` 를 커밋 에디터 기본 템플릿으로 등록하면 매번 가이드가 뜹니다.

```bash
git config commit.template .gitmessage.txt
```

## 브랜치 전략

```
main            # 배포 가능한 안정 브랜치 (직접 push 금지, PR 로만 병합)
└─ feature/#12-kakao-login   # 기능 단위 브랜치
   fix/#20-token-expiry      # 버그 수정
```

- 브랜치명: `type/#이슈번호-간단설명` (kebab-case)
- 작업은 반드시 `feature`/`fix` 브랜치에서 하고 `main` 으로 PR
- PR 은 리뷰 1인 이상 승인 후 병합

## PR / 이슈

- PR 생성 시 `.github/pull_request_template.md` 자동 적용 → 연관 이슈·작업 내용 작성
- 이슈 생성 시 `.github/ISSUE_TEMPLATE` 템플릿 사용

## 코드 스타일

- 커밋/PR 전 `./gradlew spotlessApply` 로 포맷 정렬
- `./gradlew check` 로 Spotless + Checkstyle 통과 확인

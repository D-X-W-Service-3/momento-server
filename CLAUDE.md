# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 따라야 할 지침입니다.

## 프로젝트

Momento — 타임캡슐·추억 아카이빙 서비스의 백엔드(Spring Boot). 카카오 소셜 로그인, 편지/타임캡슐, 기념일/캘린더, 추억 아카이빙(S3 이미지), 알림, 마이페이지. 이미지 생성 등 AI 기능은 **별도 AI 서버**를 OpenFeign 으로 연동한다.

- Backend 4 · Frontend 3 · AI 4

## 명령어

```bash
./gradlew bootRun          # 로컬 실행 (프로필 local, 설정 없으면 H2 인메모리로 구동)
./gradlew build            # 전체 빌드 + 테스트 + spotless/checkstyle 검사
./gradlew test             # 테스트만
./gradlew spotlessApply    # 코드 포맷 자동 정렬 (커밋 전 필수)
./gradlew check            # spotless + checkstyle 검사만
```

- Swagger: http://localhost:8080/swagger-ui.html · Health: http://localhost:8080/health-check

## 아키텍처

패키지 루트: `com.momento.server`

- `domain/{도메인}/` — 기능별 패키지. 하위: `controller`(`{Domain}Api` 인터페이스 + `{Domain}Controller`), `dto/{request,response}`, `entity`, `repository`, `service`, `facade`, `exception`, 필요 시 `external`(FeignClient)
- `global/common/` — `annotation`, `auth`(카카오 OAuth2 + JWT), `code`(ErrorCode 체계), `config`, `dto`(CommonResponse), `entity`(BaseTimeEntity), `exception`, `property`
- `global/controller/` — HealthCheck 등 도메인 무관 컨트롤러

## 코드 컨벤션 (반드시 준수)

- **응답**: 모든 API 는 `CommonResponse<T>` 반환. `CommonResponse.ok(data)` / `CommonResponse.ok()` / `CommonResponse.success(SuccessCode.CREATED, data)`.
- **컨트롤러**: `@RestController` 대신 `@RestApiController("/v1/...")` 사용. 컨트롤러는 `{Domain}Api` 인터페이스(Swagger 문서 애노테이션 위치)를 구현한다.
- **계층 흐름**: `Controller → Facade → Service → Repository`. 컨트롤러는 얇게 유지하고 조합 로직은 Facade 에 둔다.
- **예외**: 도메인별 `XxxErrorCode implements ErrorCode` enum 을 만들고 `throw new ApiException(XxxErrorCode.SOMETHING)` 로 던진다. `GlobalExceptionHandler` 가 `CommonResponse` 형태로 변환한다. 컨트롤러/서비스에서 try-catch 로 응답을 직접 만들지 않는다.
- **엔티티**: 생성/수정 시각이 필요하면 `BaseTimeEntity` 상속. 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 생성은 `@Builder` 사용.
- **인증**: 컨트롤러에서 로그인 사용자는 `@AuthenticationPrincipal UserPrincipal principal` 로 받고 `principal.getUserId()` 사용.

## 포맷 / 스타일

- **Spotless(google-java-format, 2-space 들여쓰기)** 적용됨. 코드 수정 후 **반드시 `./gradlew spotlessApply` 실행** 후 커밋한다. 안 하면 CI/빌드의 `spotlessCheck` 에서 실패한다.
- Checkstyle: star import 금지, 미사용 import 금지, 중괄호 필수, 한 줄 한 문장.

## 커밋

- 컨벤션은 [CONTRIBUTING.md](./CONTRIBUTING.md) 참고. 형식: `<type>: <제목>`
- type: `init` `feat` `fix` `build` `chore` `ci` `docs` `style` `refactor` `test` `perf`
- 기본 브랜치는 `develop`. `main`/`develop` 직접 push 금지 — `feat/#이슈-설명` 브랜치에서 작업 후 `develop` 으로 PR.

## 주의점

- **ERD 미확정**: `domain/user/User` 는 카카오 로그인에 필요한 최소 필드(email, socialProvider, socialId, nickname)만 있다. 도메인 엔티티/연관관계는 ERD 확정 후 추가한다. 지금 임의로 스키마를 설계하지 말 것.
- **DB 설정 건드리지 말 것**: 로컬은 H2 자동 구동, 운영은 `application-prod.yml`(MySQL, `ddl-auto: validate`). 명시적 요청 없이 datasource/ddl 설정을 바꾸지 않는다.
- **시크릿**: 카카오/JWT/AWS 값은 환경변수 주입(로컬 기본값은 개발용 더미). 실제 키를 코드/`application.yml` 에 하드코딩하지 않는다. `application-local.yml` 은 gitignore 대상.
- **AI 서버 연동**: FeignClient 는 `domain/{도메인}/external` 에 두고 URL 은 `${external.api-url.ai}` 사용. 엔드포인트는 AI 팀과 확정 후 작성한다.

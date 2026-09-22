# CLAUDE.md

이 파일은 Claude Code가 이 저장소에서 작업할 때 따라야 할 지침입니다.

## 프로젝트

Momento — 타임캡슐·추억 아카이빙 서비스의 백엔드(Spring Boot). 카카오 소셜 로그인, 편지/타임캡슐, 기념일/캘린더, 추억 아카이빙(S3 이미지), 알림, 마이페이지. 이미지 생성 등 AI 기능은 **별도 AI 서버**를 OpenFeign 으로 연동한다.

- Backend 4 · Frontend 3 · AI 4

## 명령어

```bash
./gradlew bootRun          # 로컬 실행 (프로필 local, H2 인메모리 + Flyway 마이그레이션)
./gradlew build            # 전체 빌드 + 테스트 + spotless/checkstyle 검사
./gradlew test             # 테스트만
./gradlew spotlessApply    # 코드 포맷 자동 정렬 (커밋 전 필수)
./gradlew check            # spotless + checkstyle 검사만
```

- Swagger: http://localhost:8080/swagger-ui.html · Health: http://localhost:8080/health-check

## 아키텍처

패키지 루트: `com.momento.server`

- `domain/{도메인}/` — 기능별 패키지. 하위: `controller`(`{Domain}Api` 인터페이스 + `{Domain}Controller`), `dto/{request,response}`, `entity`, `repository`, `service`, `facade`, `exception`, 필요 시 `external`(FeignClient)
- 현재 도메인: `user` `timecapsule` `letter` `memory` `anniversary` `notification` `image`
- `global/common/` — `annotation`, `auth`(카카오 OAuth2 + JWT), `code`(ErrorCode 체계), `config`, `dto`(CommonResponse), `entity`(BaseTimeEntity, BaseCreatedTimeEntity), `exception`, `property`
- `global/controller/` — HealthCheck 등 도메인 무관 컨트롤러

## 코드 컨벤션 (반드시 준수)

- **응답**: 모든 API 는 `CommonResponse<T>` 반환. `CommonResponse.ok(data)` / `CommonResponse.ok()` / `CommonResponse.success(SuccessCode.CREATED, data)`.
- **컨트롤러**: `@RestController` 대신 `@RestApiController("/v1/...")` 사용. 컨트롤러는 `{Domain}Api` 인터페이스(Swagger 문서 애노테이션 위치)를 구현한다.
- **계층 흐름**: `Controller → Facade → Service → Repository`. 컨트롤러는 얇게 유지하고 조합 로직은 Facade 에 둔다.
- **예외**: 도메인별 `XxxErrorCode implements ErrorCode` enum 을 만들고 `throw new ApiException(XxxErrorCode.SOMETHING)` 로 던진다. `GlobalExceptionHandler` 가 `CommonResponse` 형태로 변환한다. 컨트롤러/서비스에서 try-catch 로 응답을 직접 만들지 않는다.
- **엔티티**: 생성·수정 시각이 필요하면 `BaseTimeEntity`, 생성 후 시각이 바뀌지 않으면 `BaseCreatedTimeEntity` 상속. 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, 생성은 `@Builder` 사용.
- **엔티티 매핑**: 연관관계는 전부 `@ManyToOne(fetch = FetchType.LAZY)`. enum 컬럼은 `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)` 를 같이 붙인다(안 붙이면 네이티브 ENUM 으로 생성됨). 소프트 삭제 테이블은 `deletedAt` 을 채우는 방식.
- **인증**: 컨트롤러에서 로그인 사용자는 `@AuthenticationPrincipal UserPrincipal principal` 로 받고 `principal.getUserId()` 사용.
- **현재 시각**: 마감 · 공개 · 만료처럼 현재 시각에 따라 성공과 실패가 갈리는 로직은 `LocalDateTime.now()` 를 직접 부르지 않는다. `java.time.Clock` 빈을 주입받아 `LocalDateTime.now(clock)` 으로 읽는다. 테스트에서 시각을 고정해 경계(마감 1초 전 · 정각)를 검증하기 위해서다.

## 포맷 / 스타일

- **Spotless(google-java-format, 2-space 들여쓰기)** 적용됨. 코드 수정 후 **반드시 `./gradlew spotlessApply` 실행** 후 커밋한다. 안 하면 CI/빌드의 `spotlessCheck` 에서 실패한다.
- Checkstyle: star import 금지, 미사용 import 금지, 중괄호 필수, 한 줄 한 문장.

## 커밋

- 컨벤션은 [CONTRIBUTING.md](./CONTRIBUTING.md) 참고. 형식: `<type>: <제목>`
- type: `init` `feat` `fix` `build` `chore` `ci` `docs` `style` `refactor` `test` `perf`
- 기본 브랜치는 `develop`. `main`/`develop` 직접 push 금지 — `feat/#이슈-설명` 브랜치에서 작업 후 `develop` 으로 PR.
- **제목은 명사형으로 끝낸다** (`추가한다` ✕ → `추가` ○). CONTRIBUTING.md 규칙이다.

### 커밋 나누기 (리뷰 단위)

리뷰어가 커밋 하나를 열었을 때 "무엇을 왜 바꿨는지" 한 번에 읽히는 크기로 나눈다. 기능 전체를 한 커밋에 몰지 않는다.

- **새 API 는 아래 순서로 나눈다.** 해당 없는 단계는 건너뛴다. API 가 둘 이상이면 API 마다 3~6 을 반복한다.
  1. 공통(`global/`) 수정 — 다른 도메인에도 영향이 가므로 항상 따로
  2. 엔티티 도메인 메서드 · 에러 코드 (도메인 규칙이면 단위 테스트를 같은 커밋에)
  3. Repository — 쿼리
  4. 요청 · 응답 DTO — 검증 규칙 포함
  5. Service — 비즈니스 로직과 트랜잭션 경계
  6. Facade · `{Domain}Api` · Controller — 엔드포인트 노출, **통합 테스트를 같은 커밋에**
- **모든 커밋은 그 시점에 `./gradlew build` 가 통과해야 한다.** 중간 커밋이 깨지면 되돌리기와 원인 추적이 안 된다. `spotlessApply` 는 커밋마다 돌리고 `style: spotless 적용` 같은 포맷 전용 커밋을 따로 만들지 않는다.
- **동작 변경과 이름 변경 · 파일 이동 · 정리를 한 커밋에 섞지 않는다.** 섞이면 리뷰어가 무엇이 동작을 바꿨는지 가려내야 한다.
- **리뷰 반영은 새 커밋으로 올린다.** 무엇을 고쳤는지 PR 에서 따로 보이게 한다. (squash 머지라 develop 이력은 PR 하나로 합쳐진다.)
- 테스트를 제외한 변경이 대략 200줄이나 파일 5개를 넘으면 더 나눌 수 있는지 먼저 본다.

## 주의점

- **받은 명세서를 그대로 믿지 말 것**: 팀원이 주는 API 명세서는 빠르게 작성된 문서라 빠진 것과 틀린 것이 실제로 나온다(경로 중복, 같은 경로가 두 기능에 배정, enum 값 이름이 엔티티와 불일치 등). 코드와 어긋나거나 이상하면 **임의로 한쪽을 고르지 말고 작성자에게 확인**한다. 값 목록이 확정되지 않은 필드는 enum 대신 `String` 으로 두고 확정 후 전환한다. ERD(`docs/Momento.sql`)도 마찬가지다.
- **스키마 기준은 마이그레이션**: 실제로 DB 를 만드는 것은 `src/main/resources/db/migration/` 의 Flyway 마이그레이션이고, 이게 유일한 기준이다. [docs/Momento.sql](./docs/Momento.sql) 은 전체 스키마를 한눈에 보는 **설계 문서**이며 실행되지 않는다.
  - 스키마를 바꾸려면 **엔티티와 새 마이그레이션(`V2__...sql`)을 같이** 고친다. 한쪽만 고치면 `ddl-auto: validate` 가 잡아 `./gradlew test` 가 깨진다 — 의도된 동작이다.
  - **이미 머지된 마이그레이션 파일은 고치지 않는다.** 체크섬이 기록돼 있어 수정하면 다음 실행이 실패한다. 잘못된 건 새 버전으로 고친다.
  - 컬럼 추가·타입 변경은 여러 도메인이 동시에 작업 중이라 충돌 위험이 크다. 임의로 고치지 말고 합의한 뒤 별도 이슈로 반영한다.
- **미확정 값**: `letters.theme_type`(편지지 테마)과 `notifications.reference_type`(알림이 가리키는 리소스 종류)은 값 목록이 정해지지 않아 `String` 이다. 각각 편지 API·알림 API 작업에서 값을 확정한 뒤 enum 으로 전환한다.
- **DB 설정 건드리지 말 것**: 로컬·테스트는 H2 를 MySQL 호환 모드로 띄우고(`application.yml`), 운영은 `application-prod.yml`(MySQL). **양쪽 모두 `ddl-auto: validate` 이고 스키마는 Flyway 가 만든다.** 명시적 요청 없이 datasource/ddl 설정을 바꾸지 않는다.
  - `application-local.yml` 에 `ddl-auto` 를 다시 넣지 않는다. 넣으면 마이그레이션과 엔티티가 어긋나도 로컬에서 드러나지 않는다.
- **시크릿**: 카카오/JWT/AWS 값은 환경변수 주입(로컬 기본값은 개발용 더미). 실제 키를 코드/`application.yml` 에 하드코딩하지 않는다. `application-local.yml` 은 gitignore 대상.
- **AI 서버 연동**: FeignClient 는 `domain/{도메인}/external` 에 두고 URL 은 `${external.api-url.ai}` 사용. 엔드포인트는 AI 팀과 확정 후 작성한다.

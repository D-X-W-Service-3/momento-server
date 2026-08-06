# Momento Server

> "소중한 순간을 오래도록 기억하다" — 타임캡슐 및 추억 아카이빙 서비스 Momento의 백엔드 서버

Moment(순간) + Memento(기억을 간직하는 기념품)

## 기술 스택

| 구분 | 내용 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.4 |
| Build | Gradle (Wrapper 8.14.3) |
| DB | MySQL (운영) / H2 (로컬) |
| Auth | Spring Security + OAuth2 (카카오) + JWT |
| 외부 연동 | OpenFeign (별도 AI 서버) |
| Storage | AWS S3 |
| Docs | springdoc-openapi (Swagger UI) |
| Code Style | Spotless (google-java-format) + Checkstyle |

## 실행 방법

```bash
# 로컬 실행 (기본 프로필 local, 별도 설정 없으면 H2 인메모리로 구동)
./gradlew bootRun

# 빌드 & 테스트
./gradlew build

# 코드 포맷 적용 / 검사
./gradlew spotlessApply
./gradlew check
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health Check: http://localhost:8080/health-check

로컬 환경설정은 `src/main/resources/application-local.yml.example` 를 복사해 사용하세요.
카카오/AWS 등 시크릿은 환경변수로 주입합니다 (기본값은 개발용 더미).

## 패키지 구조

```
com.momento.server
├─ domain/                     # 기능(도메인)별 패키지 — 도메인 추가 시 아래 규약을 따름
│  └─ {domain}/
│     ├─ controller/           # {Domain}Api (인터페이스, Swagger) + {Domain}Controller
│     ├─ dto/{request,response}
│     ├─ entity/
│     ├─ repository/
│     ├─ service/
│     ├─ facade/               # Controller → Facade → Service 계층
│     ├─ exception/            # {Domain}ErrorCode (implements ErrorCode)
│     └─ external/             # 외부 API FeignClient (예: AI 서버)
│  └─ user/                    # 로그인에 필요한 최소 User 엔티티 (ERD 확정 후 확장)
└─ global/
   ├─ common/
   │  ├─ annotation/           # @RestApiController
   │  ├─ auth/                 # 카카오 OAuth2 + JWT 인증 뼈대
   │  │  ├─ filter/            # TokenAuthenticationFilter
   │  │  ├─ oauth/             # OAuth2Provider, OAuth2UserInfo + userinfo/, handler/
   │  │  └─ service/           # TokenProvider, CustomOAuth2UserService, UserPrincipalService
   │  ├─ code/                 # ErrorCode, GlobalErrorCode, SuccessCode
   │  ├─ config/               # Security/Swagger/Jpa/Feign/Async 설정
   │  ├─ dto/                  # CommonResponse, CommonResponseBodyAdvice
   │  ├─ entity/               # BaseTimeEntity (createdAt/updatedAt 감사)
   │  ├─ exception/            # ApiException, GlobalExceptionHandler
   │  └─ property/             # JwtProperties
   └─ controller/              # HealthCheckController
```

## 공통 규약

- **응답**: 모든 API 는 `CommonResponse<T>` 로 감싸 반환한다. (`CommonResponse.ok(data)`, `CommonResponse.ok()`)
- **컨트롤러**: `@RestController` 대신 `@RestApiController("/v1/...")` 를 사용한다.
- **예외**: 도메인별 `XxxErrorCode implements ErrorCode` 를 정의하고 `throw new ApiException(errorCode)` 로 던진다.
- **엔티티**: 생성/수정 시각이 필요하면 `BaseTimeEntity` 를 상속한다.
- **계층**: `Controller → Facade → Service → Repository` 흐름을 따른다.

## 협업 규칙

커밋 컨벤션 · 브랜치 전략 · PR/이슈 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md) 참고.

```bash
# 커밋 메시지 템플릿 등록 (최초 1회)
git config commit.template .gitmessage.txt
```

## 개발 인원

- Backend 4 · Frontend 3 · AI 3
- AI 서버는 별도 개발되며 본 서버에서 OpenFeign 으로 외부 API 연동

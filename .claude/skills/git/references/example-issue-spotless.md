# 작성 예시 — 이슈 (Spotless 도입)

**이슈는 이 정도 깊이를 목표로 한다.** 아래는 다른 저장소에서 작성된 실제 이슈라
경로(`docs/conventions/`, `build.gradle.kts`, `ModularityTests`)는 이 저장소와 다르다.
**구조와 밀도만 참고한다.**

특히 볼 것:

- 문제를 **저장소에서 확인한 사실**로 쓴다 — "Java 파일 34개", ".editorconfig 도 없고"
- 결정마다 **대안의 장점을 먼저 인정**하고 우리 상황에서 왜 안 맞는지 근거를 든다
- **이번 범위에서 제외한 것**을 이름으로 적는다
- **⚠️ 짚고 갈 것** — 조사하다 발견한, 이 작업의 전제를 흔드는 사실이 있으면 남긴다.
  이 예시에서는 "문서에는 CI 게이트가 있다고 적혀 있는데 `.github/workflows` 가 없다" 였다.
  이런 게 없는 작업이면 이 섹션은 생략한다
- **구현 메모** — 구현자가 빠질 함정을 미리 막는다 ("설정 커밋과 일괄 포맷 커밋을 분리한다")

---

## 어떤 기능인가요?

코드 포맷을 빌드에서 강제하도록 Spotless를 도입한다.

현재 저장소에는 포맷을 강제하는 장치가 전혀 없다. `.editorconfig`도 없고
`docs/conventions/coding-style.md`에도 들여쓰기·import 순서·줄 길이에 대한 규정이 없다.
지금은 참여 인원이 적어 드러나지 않지만, 인원이 늘면 IDE 설정 차이 때문에
"로직은 그대로인데 공백·import 순서만 바뀐 diff"가 PR 리뷰에 섞여 들어온다.

Spotless를 넣으면 두 가지가 생긴다.

- `./gradlew spotlessApply` — 규칙대로 코드를 자동 수정
- `./gradlew spotlessCheck` — 규칙 위반 시 빌드 실패 (`check`에 자동 연결)

## 완료 기준

- 저장소의 Java 파일 34개 전체가 규칙에 맞게 포맷되어 있다
- `./gradlew clean build`가 통과한다 (컴파일·테스트·`ModularityTests.verify()`)
- 포맷이 어긋난 파일이 생기면 `./gradlew check`가 실패한다
- `.git-blame-ignore-revs`에 일괄 포맷 커밋이 등록되어 blame 추적이 깨지지 않는다

## 결정 사항과 근거

### 1. 포맷터는 google-java-format AOSP 스타일 (들여쓰기 4칸, 한 줄 100자)

google-java-format 기본값은 들여쓰기 2칸인데, AOSP 프로파일은 4칸이라
기존 코드 스타일과 차이가 가장 적다.

### 2. ratchet 대신 전체 일괄 포맷

Spotless에는 `ratchetFrom("origin/main")` 옵션이 있다. 매 빌드마다 origin/main 대비
변경된 파일만 검사하는 방식이라, 대규모 리포맷 커밋 없이 손대는 파일부터 점진적으로
포맷할 수 있다.

이번엔 쓰지 않기로 했다.

- ratchet의 장점은 "리뷰 불가능한 대규모 diff를 피하는 것"인데,
  이 저장소는 Java 파일이 34개뿐이라 그 장점이 거의 없다
- 반대로 아무도 건드리지 않는 파일은 무기한 미포맷으로 남는다.
  포맷 적용 속도가 "그 파일을 고칠 일이 생기는 속도"에 묶이기 때문
- CI에서 shallow clone(`actions/checkout` 기본값 `fetch-depth: 1`)과 충돌해
  빌드가 깨지는 함정도 따라온다

프로젝트 초기인 지금이 일괄 포맷 비용이 가장 싼 시점이라 전체 적용으로 간다.
현재 열린 PR이 0개라 다른 사람 작업과 충돌할 여지도 가장 적다.

### 3. 이번 범위에서 제외한 것

pre-commit 훅, Java 외 파일(yaml/gradle.kts/md) 포맷 규칙, `.editorconfig` 추가.

## 📝 작업 상세 내용

- [ ] `gradle/libs.versions.toml`에 spotless 플러그인 추가
- [ ] 루트 `build.gradle.kts`의 `subprojects {}`에 spotless 적용 (google-java-format AOSP)
- [ ] `spotlessCheck`가 `check`에 연결되는지 확인
- [ ] `./gradlew spotlessApply`로 전체 코드 일괄 포맷 (설정과 별도 커밋)
- [ ] `.git-blame-ignore-revs`에 포맷 커밋 등록
- [ ] `./gradlew clean build`로 컴파일·테스트·`ModularityTests.verify()` 통과 확인
- [ ] 포맷을 일부러 어겼을 때 `./gradlew check`가 실패하는지 확인

## ⚠️ 짚고 갈 것

**CI 워크플로가 존재하지 않는다**

`docs/conventions/git-convention.md` 3-4절은 "CI 필수 체크: `gradle check`가 통과해야
Merge할 수 있다"고, 4절은 `deploy-dev.yml` / `deploy-prod.yml` 배포 파이프라인을 설명하고 있다.
그런데 `.github/workflows` 디렉터리 자체가 없다. 문서에 적힌 CI 게이트가 실제로는 존재하지 않는다.

따라서 이번 작업으로 `spotlessCheck`를 `check`에 물려도, 실제 강제력은 각자 로컬에서
`./gradlew check`를 돌릴 때만 생긴다. PR 단계에서 자동으로 막히지 않는다.
CI 워크플로 작성을 별도 이슈로 올려야 이 작업이 실효를 갖는다.

**IDE 설정 안내가 필요하다**

google-java-format은 import를 알파벳순 단일 블록으로 정렬한다. IntelliJ 기본 설정과 다르면
저장할 때마다 IDE와 spotless가 서로 다른 결과를 만들어 매번 `spotlessApply`로 되돌리게 된다.
머지 전에 IntelliJ google-java-format 플러그인 설치를 공지해야 한다.

## 컨벤션 근거

- `architecture.md` 4-1절 — CI 필수 체크(`gradle check`). `spotlessCheck`를 여기에 얹는다
- `git-convention.md` 3-4절 — Merge 게이트로서의 `gradle check`
- `coding-style.md` — 포맷 규정이 전혀 없다. 이번 작업이 사실상 첫 포맷 기준이 되므로,
  머지 후 "포맷은 spotless가 강제한다" 절 추가가 필요하다 (후속 작업)

## 구현 메모

- 루트 `build.gradle.kts`의 기존 `alias(libs.plugins.springBoot) apply false` +
  `subprojects {}` 공통 설정 패턴을 그대로 따른다. 새 패턴을 만들지 않는다
- `libs` 액세서는 `subprojects {}` 내부에서 직접 참조되지 않는다. 카탈로그 값이 필요하면
  기존 lombok 설정처럼 `rootProject.libs`로 접근 (기존 주석 참고)
- 설정 커밋과 일괄 포맷 커밋을 반드시 분리한다. 섞으면 리뷰어가 "설정 몇 줄"과
  "34개 파일 리포맷"을 한 diff에서 봐야 한다

## 📁 참고 자료

- Spotless Gradle 플러그인
- google-java-format
- git blame 무시 목록 (`.git-blame-ignore-revs`)

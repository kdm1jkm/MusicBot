# Development Workflow

이 [kdm1jkm/MusicBot](https://github.com/kdm1jkm/MusicBot) 포크는 개발 통합용 `dev`와 정식 버전용 `main`을 분리합니다. 버전은 upstream의 0.x와 독립적인 `YYMM.major.minor`이며 현재 기준은 `2610.1.0`입니다.

## Branch Strategy

- **`dev`**: 기본 브랜치이자 최신 개발 버전. feature PR과 일반 개발 작업은 여기로 통합합니다.
- **`main`**: 검증된 dev를 merge commit으로 승격하는 정식 버전 브랜치. 새 merge commit과 정식 태그를 한 번의 atomic push로 반영합니다.
- 원격 `master`는 제거했습니다. 기존 clone의 로컬 브랜치나 다른 worktree는 자동 삭제하지 않습니다.
- `main` 전환 시 기존 master SHA를 그대로 보존합니다. 태그 요구사항은 전환 후 들어오는 커밋에 적용하며 과거 이력을 다시 쓰지 않습니다.

### Short-Lived Branches

|브랜치|용도|예시|
|---|---|---|
|`feature/<slug>`|기능|`feature/new-player-ui`|
|`fix/<slug>`|버그 수정|`fix/youtube-playback`|
|`chore/<slug>`|유지보수·문서|`chore/update-gradle-plugins`|
|`deps/<slug>`|의존성 실험|`deps/test-lavaplayer-2-3`|
|`release/<version>`|선택적 안정화|`release/2610.1.0`|

slug는 소문자·숫자·하이픈만 허용합니다. 안정화 브랜치도 먼저 dev에 통합합니다. 정식 태그를 별도 브랜치에서 임의 생성하지 않습니다.

## Development Process

```bash
git clone https://github.com/kdm1jkm/MusicBot.git
cd MusicBot
git remote add upstream https://github.com/arif-banai/MusicBot.git
git switch dev
git pull --ff-only origin dev
git switch -c feature/my-new-feature
```

1. 작은 변경을 작성하고 검증합니다.
2. 필요하면 `origin/dev`를 fetch하여 작업 브랜치에 rebase 또는 merge합니다.
3. 명시적으로 `dev`를 대상으로 PR을 생성합니다.
4. CI 및 리뷰를 통과한 후 사람이 merge합니다. feature PR은 squash merge를 권장합니다.

커밋 메시지는 영어 `<type>: <description>` 형식입니다. 허용 type 예시: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`.

## Verification

JDK 25와 Gradle Wrapper 9.1.0을 사용합니다. Windows에서는 `./gradlew`를 `.\gradlew.bat`로 바꿉니다.


|검증|명령|
|---|---|
|전체 빌드·단위/통합 테스트·shaded JAR|`./gradlew --no-daemon build`|
|단위/통합 테스트|`./gradlew --no-daemon test integrationTest`|
|커버리지|`./gradlew jacocoTestReport jacocoIntegrationTestReport`|
|선택적 lint|`./gradlew checkstyleMain checkstyleTest -Pcheckstyle`|
|선택적 정적 분석|`./gradlew spotbugsMain -Pspotbugs`|
|Docker 로컬 빌드|`docker build -t jmusicbot:2610.1.0 .`|

산출물은 `build/libs/JMusicBot-<version>-All.jar`입니다. YouTube의 공식 `v2`/`common` 의존성은 `build.gradle`의 `youtubeSourceVersion`으로 upstream commit `2be8e542d3f6f178e048dca565892684c2e40177`, 불변 빌드 `20260917.205250-1`에 함께 고정합니다. release 1.18.2에 없는 Android/iOS 재생 수정을 포함합니다. yt-dlp/Deno 실행 파일은 다운로드하거나 포함하지 않으며 지원 플랫폼의 Docker에서도 동일한 내장 youtube-source 백엔드를 사용합니다.

## CI/CD Workflows

### Build and Test

`build-and-test.yml`은 dev/main 대상 PR과 dev/main의 모든 push를 검사합니다. 문서·workflow 변경도 제외하지 않습니다. Linux **Build and Test**와 **Windows x64 Build**가 모두 성공해야 합니다. 수동 실행은 진단용이며 자동 beta 태그 자격은 push/dev 성공 run에만 있습니다.

Windows도 동일한 Gradle 빌드와 단위·통합 테스트를 실행합니다. 폐기된 yt-dlp/Deno 도구 추출·버전 smoke는 실행하지 않습니다. Android/iOS는 재생용, Android VR은 메타데이터 전용입니다. upstream embedded 보조 요청은 별도 HTTP 클라이언트를 사용하므로 `ProxiedWebEmbeddedClient`가 같은 오디오 프록시를 적용합니다.

### Development Tags

`tag-dev.yml`은 동일 저장소의 성공한 push/dev CI를 재조정합니다. GitHub concurrency의 pending 실행 교체로 알림이 합쳐져도 성공 run 이력을 페이지 끝까지 조회하여 누락된 SHA를 처리합니다.

- 버전 기준은 대상 SHA의 `gradle.properties`입니다.
- 형식: `v2610.1.0.beta-1`, `v2610.1.0.beta-2`, ….
- beta 번호는 동일 버전의 기존 원격 태그 중 최댓값 다음입니다. 삭제·재사용하지 않습니다.
- 이미 태그가 있는 SHA는 CI 재실행으로 중복 태그를 만들지 않습니다.
- 실패·취소·PR CI는 태그하지 않습니다. 이후 dev가 전진해도 성공한 과거 SHA가 dev 이력에 남아 있으면 해당 SHA를 태그합니다.
- 자동화 workflow 정의는 기본 브랜치 dev에 있어야 활성화됩니다. 구현 PR merge 전에는 동작하지 않습니다.

### Main Promotion

1. 원하는 정식 버전을 `gradle.properties`에 반영하는 PR을 dev에 통합합니다.
2. dev HEAD의 Linux/Windows push CI 성공과 beta 태그를 확인합니다.
3. **Promote Main**을 **dev**에서 수동 실행합니다. 정식 버전과 검토한 dev HEAD의 전체 SHA를 입력합니다.
4. workflow는 최신 원격 dev와 입력 SHA, 버전, 성공 CI, beta 태그를 확인합니다.
5. main을 첫 부모, 선택한 dev HEAD를 둘째 부모로 하는 merge commit을 만들고 `vYYMM.major.minor` 태그와 함께 atomic push합니다.

main HEAD를 직접 수정하거나 GitHub 웹 merge로 dev를 합치지 않습니다. 승격 중 dev/main이 변경되거나 버전/태그가 충돌하면 실패하며 임의 재시도·강제 push하지 않습니다. 이미 main에 포함된 dev는 새 승격 대상이 아닙니다. 다음 개발 버전은 dev의 `gradle.properties`를 일반 PR로 올립니다. 승격 workflow는 버전 변경 커밋을 추가하지 않습니다.

### Remote Enforcement and Credentials

- `main tagged promotion only`: main update를 제한하고 DeployKey actor만 예외로 둡니다.
- `main immutable history`: 예외 없이 main 삭제·강제 push를 차단합니다.
- `immutable version tags`: 예외 없이 `v*` 태그 변경·삭제를 차단합니다.
- 승격 write deploy key의 private key는 `main-promotion` environment의 `MAIN_PROMOTION_KEY`에만 저장합니다. environment 실행 브랜치는 dev로 제한합니다. 로그나 저장소에 키를 출력/커밋하지 않습니다.
- GitHub의 DeployKey 예외는 특정 key ID가 아니라 **deploy key 종류 전체**에 적용됩니다. 다른 write deploy key 추가는 main 승격 권한 추가와 같으므로 관리자가 검토해야 합니다. 저장소 workflow 작성 권한과 environment 정책 변경 권한도 신뢰 경계입니다.
- 일반 관리자 push도 main update 규칙을 우회하지 못합니다. 저장소 관리자가 규칙 자체를 변경할 수 있다는 GitHub 관리 권한의 한계는 남습니다.
- 키 교체 시 새 write deploy key를 등록하고 environment secret을 교체한 다음 이전 key를 폐기합니다. 개인 PAT로 우회하지 않습니다.

### Existing Manual Release Publication

**Make Release**는 main에 이미 승격된 정식 태그만 읽어 빌드하고 기존 방식으로 GitHub Release 초안을 만듭니다. 버전 bump 커밋·태그 생성·브랜치 push는 하지 않습니다. 새 Release 자동 연동은 이 변경의 범위가 아닙니다. 배포 전 Java 의존성의 라이선스·third-party notices를 확인합니다.

### Docker Publication

`docker-publish.yml`은 승격된 정식 태그에서만 버전·SHA·latest 이미지를 발행합니다. beta 태그로 latest를 갱신하지 않습니다. `publish-preview-image.yml`은 별도 수동 preview 경로입니다. 설정 존재만으로 registry 발행 성공을 의미하지 않으며 실제 실행 결과를 확인합니다.

## Existing Clones

```bash
git fetch origin --prune
git remote set-head origin -a
# 사용 중인 worktree의 변경을 먼저 보존한 뒤 필요에 따라 수행:
git switch --track origin/dev
```

다른 worktree가 사용 중인 로컬 master를 강제로 rename/delete하지 않습니다. 일반 PR은 dev, 정식 승격은 Promote Main이라는 구분을 유지합니다.

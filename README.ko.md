<img align="right" src="https://i.imgur.com/zrE80HY.png" height="200" width="200">

# JMusicBot

[English](README.md) | **한국어**

> [!NOTE]
> [kdm1jkm/MusicBot](https://github.com/kdm1jkm/MusicBot)에서 독립적으로 관리하는 포크입니다. [jagrosh/MusicBot](https://github.com/jagrosh/MusicBot)을 이어 개발한 [arif-banai/MusicBot](https://github.com/arif-banai/MusicBot)을 기반으로 합니다.
> 업스트림의 0.x 릴리스와 별개로 **2610.1.0**부터 자체 버전 체계를 사용합니다. 원작자 표시와 라이선스 고지는 유지합니다.

[![다운로드](https://img.shields.io/github/downloads/kdm1jkm/MusicBot/total.svg)](https://github.com/kdm1jkm/MusicBot/releases/latest)
[![스타](https://img.shields.io/github/stars/kdm1jkm/MusicBot.svg)](https://github.com/kdm1jkm/MusicBot/stargazers)
[![릴리스](https://img.shields.io/github/release/kdm1jkm/MusicBot.svg)](https://github.com/kdm1jkm/MusicBot/releases/latest)
[![라이선스](https://img.shields.io/github/license/kdm1jkm/MusicBot.svg)](https://github.com/kdm1jkm/MusicBot/blob/main/LICENSE)
[![빌드 및 테스트](https://github.com/kdm1jkm/MusicBot/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/kdm1jkm/MusicBot/actions/workflows/build-and-test.yml)

깔끔한 인터페이스를 제공하는 Discord 음악 봇입니다. 대부분의 음원 소스는 여러 운영체제에서 사용할 수 있지만, 내장 YouTube 백엔드는 Windows x64가 필요합니다.

## Java 버전 및 필수 요구 사항

이 버전에서는 여러 의존성을 변경하거나 업데이트했습니다. 정상적인 실행을 위해 다음 요구 사항을 확인하세요.

- **Java 25 이상:** 봇을 실행하려면 **Java 25 이상**이 필요합니다.
- **LibDave/udpqueue:** Linux에서는 **glibc 2.38 이상**이 필요합니다. Docker 이미지에는 이 요구 사항을 충족하는 환경이 포함됩니다.
- **Privileged Gateway Intents:** [Discord Developer Portal](https://discord.com/developers/applications)에서 **Message Content Intent**를 활성화해야 합니다.
  - 해당 애플리케이션의 **Bot > Privileged Gateway Intents > Message Content Intent**를 켜세요.
  - 비활성화되어 있으면 봇이 메시지 기반 명령을 읽을 수 없습니다.

## 주요 기능

- Java를 설치한 뒤 JAR 파일로 실행
- 빠른 음원 로딩과 부드러운 재생
- Discord 봇 토큰 외에 별도의 외부 API 키 불필요
- 음악 재생을 관리하는 서버별 DJ 역할
- 메뉴 기반 인터페이스
- YouTube, SoundCloud 등 여러 음원 소스 지원
- 인터넷 라디오와 온라인 스트림 지원
- 로컬 파일 지원
- 웹·YouTube 및 로컬 재생목록 지원

## 지원 소스 및 형식

[lavaplayer](https://github.com/lavalink-devs/lavaplayer#supported-formats)의 음원 소스와 형식을 지원합니다. YouTube에는 아래의 운영체제 제한이 적용됩니다.

### 음원 소스

- YouTube — Windows x64, 내장 yt-dlp 사용
- SoundCloud
- Bandcamp
- Vimeo
- Twitch 스트림
- 로컬 파일
- HTTP URL

### 파일 및 스트림 형식

- MP3
- FLAC
- WAV
- Matroska/WebM — AAC, Opus 또는 Vorbis 코덱
- MP4/M4A — AAC 코덱
- OGG 스트림 — Opus, Vorbis 및 FLAC 코덱
- AAC 스트림
- 스트림 재생목록 — M3U 및 PLS

## 실행 예시

![음원 로딩 예시](https://i.imgur.com/kVtTKvS.gif)

## 설정

1. **Java 25 이상**을 설치하고 `java -version`으로 확인합니다. YouTube를 재생하려면 **Windows x64**를 사용하세요. Linux에서는 [시스템 요구 사항](#linux-시스템-요구-사항)의 네이티브 라이브러리도 필요합니다.
2. 이 포크의 [릴리스](https://github.com/kdm1jkm/MusicBot/releases)에서 `JMusicBot-2610.1.0-All.jar`를 다운로드하거나, 아래에 설명한 Gradle Wrapper로 직접 빌드합니다. JAR 파일은 봇 전용 디렉터리에 둡니다.
3. [Discord Developer Portal](https://discord.com/developers/applications)에서 애플리케이션과 봇을 생성하고 **봇 토큰**을 발급받습니다. Bot 페이지에서 **Message Content Intent**를 활성화하세요. 토큰은 비밀로 보관해야 합니다. OAuth2의 URL Generator에서 `bot`과 `applications.commands`를 선택하고, 음악을 사용할 채널의 메시지 보기·보내기 및 음성 채널 연결·말하기 권한을 부여합니다. 생성된 URL을 열어 봇을 서버에 초대합니다.
4. Discord의 **사용자 설정 > 고급 > 개발자 모드**를 활성화한 뒤, 자신의 프로필을 오른쪽 클릭하고 **사용자 ID 복사**를 선택합니다. 애플리케이션 ID나 서버 ID가 아닌 **봇 소유자의 사용자 ID**가 필요합니다.
5. [src/main/resources/reference.conf](src/main/resources/reference.conf)를 봇 디렉터리의 `config.txt`로 복사한 뒤 필수 항목을 설정합니다.

   ```hocon
   discord {
     token = "YOUR_BOT_TOKEN"
     owner = 123456789012345678
   }
   ```

   나머지는 필요한 경우에만 기본값을 변경하세요. Linux, macOS 또는 Docker에서는 `playback.audioSources.youtube = false`도 설정합니다. `config.txt`를 커밋하거나 토큰을 공유하지 마세요.
6. **해당 디렉터리에서** 아래 명령을 실행합니다. 설정과 재생목록의 상대 경로는 작업 디렉터리를 기준으로 합니다. Discord에서는 `/help`를 사용하거나 봇을 멘션한 뒤 `help`를 입력하세요.

## 직접 실행 — Docker 미사용

JMusicBot을 직접 실행할 때는 다음 JVM 옵션을 지정하세요.

**Linux / macOS / Windows 명령 프롬프트(CMD):**

```bash
java -Dfile.encoding=UTF-8 -Dnogui=true --enable-native-access=ALL-UNNAMED -jar JMusicBot-2610.1.0-All.jar
```

**Windows PowerShell:** PowerShell이 `-D`를 자체 매개변수로 해석하지 않도록 각 JVM 옵션을 따옴표로 감싸세요.

```powershell
java "-Dfile.encoding=UTF-8" "-Dnogui=true" "--enable-native-access=ALL-UNNAMED" "-jar" ".\JMusicBot-2610.1.0-All.jar"
```

또는 `java --% -Dfile.encoding=UTF-8 -Dnogui=true ...`처럼 중지 구문 분석 토큰 `--%`를 사용해 나머지 인수를 그대로 전달할 수 있습니다.

`-Dfile.encoding=UTF-8`은 한국어 등 비영어권 문자가 Discord에서 올바르게 표시되도록 합니다. Windows 또는 이전 JDK에서는 이 옵션이 없을 때 슬래시 명령 자동 완성이나 임베드의 문자가 깨질 수 있습니다.

### 내장 YouTube 재생 (Windows x64)

Java 25 이상에서 `JMusicBot-2610.1.0-All.jar`를 실행하세요. 단일 JAR 안에 고정 버전의 yt-dlp, EJS 스크립트와 Deno가 포함됩니다. **Python·yt-dlp·Deno·FFmpeg를 따로 설치할 필요가 없으며, 최초 실행 시 도구를 다운로드하지 않습니다.** YouTube를 처음 사용할 때 체크섬을 검증한 뒤 전용 임시 디렉터리에 도구를 추출하고, 종료 시 제거합니다. JAR 디렉터리는 읽기 전용이어도 되지만, 시스템 임시 디렉터리에서는 실행 파일의 추출과 실행이 허용되어야 합니다.

YouTube 로딩·검색·재생목록·탐색·반복 재생에는 안정적인 공개 영상 URL을 사용합니다. 실제 미디어 URL은 재생 직전에 새로 해석하고 접근 가능 여부를 확인합니다. 일반 영상은 WebM/Opus 또는 M4A/AAC로 재생합니다. 라이브는 MPEG-TS 또는 packed-AAC HLS를 지원하며 탐색할 수 없습니다. 지원하지 않는 컨테이너는 명확한 오류로 처리합니다. **Linux·macOS·Docker에서는 YouTube를 재생할 수 없으며**, 다른 활성화된 음원 소스는 계속 사용할 수 있습니다.

기본적으로 로그인 없이 공개 영상을 재생합니다. YouTube가 계정 인증을 요구하거나 IP의 접근을 거부하면, 사용자가 직접 내보낸 유효한 **Netscape 형식 쿠키 파일**을 GUI의 선택적 YouTube 쿠키 항목이나 설정에 지정하세요.

```hocon
playback.youtube.cookiesFile = "C:/MusicBot/youtube-cookies.txt"
```

상대 경로는 봇의 작업 디렉터리를 기준으로 합니다. 지정한 파일은 읽을 수 있어야 하며, 각 작업마다 별도로 복사해 사용하므로 봇이 원본을 수정하지 않습니다. 봇은 브라우저 프로필을 읽거나 인증 정보를 내보내지 않습니다. 쿠키 파일은 비밀로 보관하고 소스 저장소에 올리지 마세요. 계정을 사용하면 요청 제한이나 계정 차단이 발생할 수 있습니다. [yt-dlp의 YouTube 쿠키 안내](https://github.com/yt-dlp/yt-dlp/wiki/Extractors#exporting-youtube-cookies)를 확인하세요. 내장 도구가 있어도 비공개 영상, 계정 또는 네트워크의 접근 제한을 우회할 수는 없습니다.

설정은 자동으로 버전 3으로 마이그레이션됩니다. 이전 YouTube OAuth/cipher 설정과 백엔드는 제거되었습니다. 기존 `youtubetoken.txt` 파일은 삭제하지 않지만 더 이상 사용하지 않습니다.

빌드는 JDK 25와 고정된 Gradle 9.1.0 Wrapper를 사용합니다. `./gradlew --no-daemon build`를 실행하세요. Windows에서는 `.\gradlew.bat --no-daemon build`를 사용합니다. 단위·통합 테스트를 실행한 뒤 `build/libs/JMusicBot-2610.1.0-All.jar`를 생성합니다. Gradle을 전역으로 설치할 필요는 없으며, 버전은 `gradle.properties`에서 관리합니다. 빌드 시 체크섬으로 고정한 Windows 도구를 다운로드해 단일 `All.jar`에 포함합니다. 도구를 다운로드할 수 없거나 파일이 손상되었으면 빌드가 실패합니다. yt-dlp nightly `2026.10.10.233141`과 Deno `2.9.7`을 사용하며, 업데이트하려면 빌드 버전과 체크섬을 함께 변경해야 합니다. 런타임 자동 업데이트는 하지 않습니다. 내장 프로그램에는 각각의 라이선스가 적용되며, yt-dlp 독립 실행 배포판에는 GPLv3+ 조건이 적용됩니다. 고지와 대응 소스·빌드 출처 정보는 `META-INF/third-party/ytdlp/` 및 `META-INF/third-party/deno/`에 있습니다. 재배포 시 이 고지를 유지하고 해당 소스 제공 의무를 충족해야 합니다.

### Linux 시스템 요구 사항

**중요:** Linux에서는 **glibc 2.38 이상**이 필요합니다. 이보다 낮으면 JDave와 udpqueue 사용 중 오류가 발생할 수 있습니다.

> Ubuntu 24.04 "Noble"과 Debian 13 "Trixie"에는 호환되는 glibc가 기본으로 포함됩니다.

다음 명령으로 glibc 버전을 확인할 수 있습니다.

```bash
ldd --version
```

Debian/Ubuntu 계열에서는 다음 네이티브 오디오 라이브러리가 추가로 필요할 수 있습니다.

```bash
# 필요한 네이티브 라이브러리 설치
sudo apt-get update
sudo apt-get install -y libopus0 libsodium23
```

버전이 2.38보다 낮으면 시스템을 업그레이드하거나 호환되는 실행 환경을 사용하세요. Docker를 사용하는 방법도 권장합니다.

## Docker

이 저장소에서 로컬 이미지를 빌드하세요. 아래 예시는 포크의 이미지가 레지스트리에 이미 공개되어 있다고 가정하지 않습니다.

```bash
docker build -t jmusicbot:2610.1.0 .
```

컨테이너에는 Java 25가 포함되며 GUI 없이 실행됩니다. **내장 yt-dlp/Deno가 Windows x64 전용이므로 Linux 컨테이너에서는 YouTube를 재생할 수 없습니다.** 다른 활성화된 음원 소스는 사용할 수 있습니다.

### 빠른 시작

1. 봇 데이터를 유지할 디렉터리를 만듭니다. 기존 `config.txt`, `Playlists/` 등의 파일이 있는 디렉터리를 사용해도 됩니다.
2. [설정](#설정)에 따라 `config.txt`를 만들고 `discord.token`, `discord.owner` 및 `playback.audioSources.youtube = false`를 설정합니다.
3. 컨테이너는 UID/GID `10001:10001`로 실행됩니다. 해당 사용자가 마운트할 디렉터리를 읽고 쓸 수 있도록 권한을 부여하세요. Linux에서는 다음과 같이 설정할 수 있습니다.

   ```bash
   sudo chown -R 10001:10001 /path/to/musicbot
   ```

4. 로컬에서 빌드한 이미지로 실행합니다.

   ```bash
   docker run --rm -it \
     --name jmusicbot \
     -v "/path/to/musicbot:/musicbot" \
     jmusicbot:2610.1.0
   ```

`config.txt`가 없으면 봇이 기본 파일을 생성합니다. 필수 항목을 편집한 뒤 다시 실행하세요. 설정·재생목록·서버 설정을 유지하려면 실행할 때마다 `/musicbot`을 마운트해야 합니다.

### Docker Compose

저장소 루트에서 실행합니다.

```bash
cp docker-compose.example.yml docker-compose.yml
# docker-compose.yml의 볼륨 경로를 수정하고 위 안내에 따라 config.txt 준비
docker compose up -d --build
```

[Compose 예제](docker-compose.example.yml)는 `jmusicbot:2610.1.0`을 로컬에서 빌드합니다. `JAVA_OPTS`를 지정하면 기본 ZGC·AlwaysPreTouch 옵션을 덮어씁니다. 힙 크기를 제한하면서 해당 옵션을 유지하려면 함께 지정하세요. 자세한 내용은 [성능 조정](#성능-조정)을 참고하세요.

## 성능 조정

다음 JVM 옵션과 설정으로 오디오 끊김을 줄일 수 있습니다.

### 권장 JVM 옵션

ZGC(Z Garbage Collector)를 사용하면 GC 일시 정지 시간을 1밀리초 미만으로 줄일 수 있습니다.

```bash
java -Dfile.encoding=UTF-8 \
     -XX:+UseZGC \
     -XX:+AlwaysPreTouch \
     -Dnogui=true \
     --enable-native-access=ALL-UNNAMED \
     -jar JMusicBot-*.jar
```

**옵션 설명:**

- `-Dfile.encoding=UTF-8`: Discord에서 비영어권 문자가 올바르게 표시되도록 합니다. Windows 또는 이전 JDK에서 필요합니다.
- `-XX:+UseZGC`: GC 정지 시간을 줄입니다. JDK 24 이상에서는 세대별 모드가 기본입니다.
- `-XX:+AlwaysPreTouch`: 시작 시 메모리를 미리 할당해 페이지 폴트를 줄입니다.
- `-Xms` / `-Xmx`: 선택 사항입니다. `-Xms256m -Xmx512m`처럼 힙 크기를 제한하거나 고정할 수 있습니다.

Docker 이미지는 기본적으로 ZGC와 AlwaysPreTouch를 사용합니다. `JAVA_OPTS`로 힙 크기를 지정하거나 옵션을 변경할 수 있습니다.

```yaml
environment:
  - JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseZGC -XX:+AlwaysPreTouch
```

### 오디오 버퍼 설정

GC 정지에 대비하는 오디오 버퍼를 `config.txt`에서 조정할 수 있습니다.

```hocon
performance {
  # NAS 버퍼 길이(ms): 이 시간 이내의 JVM 정지에 대비
  nasBufferMs = 800

  # Lavaplayer 프레임 버퍼 길이(ms): 디코딩된 오디오 보관량
  frameBufferMs = 2000
}
```

버퍼가 클수록 끊김에 강해지지만 지연도 늘어납니다. 기본값인 NAS 800ms, 프레임 버퍼 2000ms는 대부분의 환경에 적합합니다.

## 프록시 설정

구성 요소별로 프록시 사용 여부를 지정할 수 있습니다. 예를 들어 오디오 요청만 프록시로 보내고 Discord API는 직접 연결할 수 있습니다. 지역별 네트워크 제한 때문에 오디오 트래픽에 프록시가 필요한 경우 유용합니다.

### 설정 방법

`config.txt`에 다음 항목을 추가합니다.

```hocon
proxy {
  # 프록시 서버 호스트와 포트
  host = "127.0.0.1"
  port = 8080

  # 구성 요소별 프록시 사용 여부
  lavaplayer = true   # 음원 요청 — YouTube, SoundCloud 등
  jda = false         # Discord API 요청
  github = false      # 버전 확인 요청
}
```

### 사용 예시

**오디오 요청만 프록시로 보내기 — 일반적인 구성:**

```hocon
proxy {
  host = "127.0.0.1"
  port = 18080
  lavaplayer = true
  jda = false
  github = false
}
```

**모든 요청을 프록시로 보내기:**

```hocon
proxy {
  host = "proxy.example.com"
  port = 8080
  lavaplayer = true
  jda = true
  github = true
}
```

### 참고 사항

- `host`를 비워 두거나 `port`를 0으로 설정하면 프록시를 사용하지 않습니다.
- 각 구성 요소의 프록시 사용 여부는 독립적으로 지정할 수 있습니다.
- HTTP 프록시를 지원합니다. SOCKS 프록시는 현재 지원하지 않습니다.

## 개발 워크플로

기본 브랜치 **`dev`**에 개발 작업을 통합하며 CI 성공마다 `vYYMM.major.minor.beta-n` 태그를 생성합니다. **`main`**에는 dev에서 승격한 정식 태그가 있는 merge commit만 반영합니다. 일반 작업은 수명이 짧은 브랜치에서 진행합니다.

- **`feature/<slug>`**: 새 기능 — 예: `feature/new-player-ui`
- **`fix/<slug>`**: 버그 수정 — 예: `fix/youtube-playback`
- **`chore/<slug>`**: 유지보수 — 예: `chore/update-deps`
- **`deps/<slug>`**: 의존성 변경 실험 — 예: `deps/update-yt-dlp`
- **`release/<version>`**: 선택적인 릴리스 안정화 — 예: `release/2610.1.0`

CI에서 브랜치 이름 규칙을 검사합니다. 개발 절차, 브랜치 이름 규칙과 권장 사항은 [개발 워크플로 문서](docs/DEVELOPMENT_WORKFLOW.md)를 참고하세요.

## 질문·제안·버그 제보

질문, 기능 요청 또는 재현 가능한 버그를 등록하기 전에 이 포크의 [이슈](https://github.com/kdm1jkm/MusicBot/issues)를 검색해 주세요. 버전, 운영체제와 관련 로그를 포함하되 **토큰과 쿠키는 제거**하세요. 업스트림의 지원 서비스는 이 포크에서 운영하지 않습니다.

이 프로젝트에 사용된 주요 라이브러리와 도구:

- [**JDA**](https://github.com/discord-jda/JDA)
- [**lavaplayer**](https://github.com/lavalink-devs/lavaplayer)
- [**yt-dlp**](https://github.com/yt-dlp/yt-dlp)와 [**EJS 스크립트**](https://github.com/yt-dlp/ejs)
- [**Deno**](https://github.com/denoland/deno)

## 소스 수정 및 라이선스

기여와 빌드 방법은 [개발 워크플로 문서](docs/DEVELOPMENT_WORKFLOW.md)를 참고하세요. 이 포크는 원작자 표시와 프로젝트의 [Apache 2.0 라이선스](LICENSE)를 유지합니다. 내장된 타사 프로그램에는 각각의 라이선스가 적용됩니다. JAR를 재배포할 때는 해당 고지를 유지하고, [내장 YouTube 재생 안내](#내장-youtube-재생-windows-x64)에 설명된 GPLv3+ 대응 소스 제공 의무도 충족해야 합니다.

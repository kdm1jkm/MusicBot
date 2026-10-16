<img align="right" src="https://i.imgur.com/zrE80HY.png" height="200" width="200">

# JMusicBot

**English** | [한국어](README.ko.md)

> [!NOTE]
> Independent fork maintained at [kdm1jkm/MusicBot](https://github.com/kdm1jkm/MusicBot), based on [arif-banai/MusicBot](https://github.com/arif-banai/MusicBot), which continued [jagrosh/MusicBot](https://github.com/jagrosh/MusicBot).
> This fork uses its own version line, starting at **2610.1.0**, independently of upstream's 0.x releases. Original author and license attribution is preserved.

[![Downloads](https://img.shields.io/github/downloads/kdm1jkm/MusicBot/total.svg)](https://github.com/kdm1jkm/MusicBot/releases/latest)
[![Stars](https://img.shields.io/github/stars/kdm1jkm/MusicBot.svg)](https://github.com/kdm1jkm/MusicBot/stargazers)
[![Release](https://img.shields.io/github/release/kdm1jkm/MusicBot.svg)](https://github.com/kdm1jkm/MusicBot/releases/latest)
[![License](https://img.shields.io/github/license/kdm1jkm/MusicBot.svg)](https://github.com/kdm1jkm/MusicBot/blob/master/LICENSE)
[![Build and Test](https://github.com/kdm1jkm/MusicBot/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/kdm1jkm/MusicBot/actions/workflows/build-and-test.yml)

A Discord music bot with a clean interface. Most sources are cross-platform; the bundled YouTube backend requires Windows x64.

## ⚠️ Check your Java Version and other requirements

This version of JMusicBot changes/updates various dependencies. To ensure your bot continues to function correctly, please note the following mandatory changes:

*   **Java 25 Minimum:** The bot now requires **Java 25 or higher**. 

*   **LibDave/udpqueue:** You **must** have **glibc >= 2.38**. *If you are using Docker, this is already handled for you.*
*   **Privileged Gateway Intents:** You **must** enable the **Message Content Intent** in your [Discord Developer Portal](https://discord.com/developers/applications).
    *   *Navigate to: Your Application > Bot > Privileged Gateway Intents > Toggle "Message Content Intent" to ON.*
    *   *Without this, the bot will not see your commands.*


## Features
  * Easy to run (just make sure Java is installed, and run!)
  * Fast loading of songs
  * No external keys needed (besides a Discord Bot token)
  * Smooth playback
  * Server-specific setup for the "DJ" role that can moderate the music
  * Clean and beautiful menus
  * Supports many sites, including Youtube, Soundcloud, and more
  * Supports many online radio/streams
  * Supports local files
  * Playlist support (both web/youtube, and local)

## Supported sources and formats
JMusicBot supports all sources and formats supported by [lavaplayer](https://github.com/lavalink-devs/lavaplayer#supported-formats):
### Sources
  * YouTube (Windows x64, bundled yt-dlp)
  * SoundCloud
  * Bandcamp
  * Vimeo
  * Twitch streams
  * Local files
  * HTTP URLs
### Formats
  * MP3
  * FLAC
  * WAV
  * Matroska/WebM (AAC, Opus or Vorbis codecs)
  * MP4/M4A (AAC codec)
  * OGG streams (Opus, Vorbis and FLAC codecs)
  * AAC streams
  * Stream playlists (M3U and PLS)

## Example
![Loading Example...](https://i.imgur.com/kVtTKvS.gif)

## Setup

1. Install **Java 25 or newer** (`java -version`). For YouTube playback, use **Windows x64**; Linux also requires the native libraries described [below](#linux-system-requirements).
2. Download `JMusicBot-2610.1.0-All.jar` from this fork's [releases](https://github.com/kdm1jkm/MusicBot/releases), or build it with the Gradle Wrapper as described below. Put it in a dedicated bot directory.
3. In the [Discord Developer Portal](https://discord.com/developers/applications), create an application and bot, obtain its **bot token**, and enable **Message Content Intent** on the Bot page. Keep the token private. On OAuth2's URL Generator, select `bot` and `applications.commands`, grant the bot permissions to view/send messages and connect/speak in your music channels, then open the generated URL to invite it to your server.
4. Enable Discord's **User Settings > Advanced > Developer Mode**, then right-click your own profile and choose **Copy User ID**. This is the owner ID, not the application or server ID.
5. Copy [src/main/resources/reference.conf](src/main/resources/reference.conf) to `config.txt` in the bot directory and set the required fields:

   ```hocon
   discord {
     token = "YOUR_BOT_TOKEN"
     owner = 123456789012345678
   }
   ```

   Keep the other defaults unless needed. On Linux, macOS, or Docker, also set `playback.audioSources.youtube = false`. Never commit `config.txt` or share its token.
6. Run the command below **from that directory**. Configuration and playlists are relative to the working directory. In Discord, use `/help` or mention the bot followed by `help`.

## Running Directly (Without Docker)

When running JMusicBot directly (not in Docker), make sure to pass these JVM flags:

**Linux / macOS / Windows (CMD):**
```bash
java -Dfile.encoding=UTF-8 -Dnogui=true --enable-native-access=ALL-UNNAMED -jar JMusicBot-2610.1.0-All.jar
```

**Windows (PowerShell):** PowerShell treats `-D` as its own parameter. Quote each JVM option so they are passed to `java` correctly:
```powershell
java "-Dfile.encoding=UTF-8" "-Dnogui=true" "--enable-native-access=ALL-UNNAMED" "-jar" ".\JMusicBot-2610.1.0-All.jar"
```
Alternatively, use the stop-parsing token so the rest of the line is passed literally: `java --% -Dfile.encoding=UTF-8 -Dnogui=true ...`

`-Dfile.encoding=UTF-8` ensures non-English characters (Cyrillic, Japanese, etc.) display correctly in Discord. On Windows or older JDKs, omitting it can cause mojibake in slash-command autocomplete and embeds.

### Bundled YouTube playback (Windows x64)

Use `JMusicBot-2610.1.0-All.jar` with Java 25 or newer. The single JAR contains pinned yt-dlp, its EJS scripts, and Deno. No separate Python, yt-dlp, Deno, or FFmpeg installation is needed; no tools are downloaded at first run. Tools are checksum-verified and extracted into a private temporary directory when YouTube is first used, then removed on shutdown. The JAR directory can be read-only; the system temporary directory must permit executable extraction and launch.

YouTube loading, search, playlists, seeking, and repeat use stable public video URLs; a fresh, accessibility-checked media URL is resolved immediately before playback. VOD uses WebM/Opus or M4A/AAC; live playback supports MPEG-TS or packed-AAC HLS and is not seekable. Other unsupported containers fail explicitly. YouTube playback is unavailable on Linux, macOS, and Docker; other enabled sources remain usable.

Anonymous public playback is the default. If YouTube requires account authentication or rejects your IP, supply a valid, locally exported **Netscape-format** cookies file in the GUI's optional YouTube cookies field or configuration:

```hocon
playback.youtube.cookiesFile = "C:/MusicBot/youtube-cookies.txt"
```

Relative paths resolve from the bot working directory. A configured file must be readable; it is copied privately for each operation, never rewritten by the bot. The bot does not read browser profiles or export credentials. Keep cookie files private and out of source control. Account use carries rate-limit/ban risk; see [yt-dlp's YouTube cookie guidance](https://github.com/yt-dlp/yt-dlp/wiki/Extractors#exporting-youtube-cookies). Bundling tools cannot override private-video, account, or network restrictions.

Configuration migrates automatically to version 3. The retired YouTube OAuth/cipher setting and backend are removed; existing `youtubetoken.txt` files are left untouched but are no longer used.

`./gradlew --no-daemon build` (Windows: `.\gradlew.bat --no-daemon build`) uses the pinned Gradle 9.1.0 Wrapper with JDK 25, runs unit and integration tests, and produces `build/libs/JMusicBot-2610.1.0-All.jar`. No global Gradle installation is needed; the version is set in `gradle.properties`. The build downloads checksum-pinned Windows assets and embeds them in the shaded `All.jar`; builds fail if assets are unavailable or corrupt. yt-dlp nightly `2026.10.10.233141` and Deno `2.9.7` are pinned; upgrading requires changing the build pins and checksums together, not runtime self-update. Bundled programs have their own licenses, including GPLv3+ terms for the standalone yt-dlp distribution. Notices and corresponding-source/build provenance are inside `META-INF/third-party/ytdlp/` and `META-INF/third-party/deno/`; redistributors must preserve notices and satisfy the applicable source-access obligations.


### Linux System Requirements

**Important:** Your system **must have glibc version 2.38 or higher**. Failure to meet this requirement will result in errors when using JDave and udpqueue.

> **Note:** Ubuntu 24.04 "Noble" and Debian 13 "Trixie" already include a compatible glibc version out of the box.

You can check your glibc version with:
```bash
ldd --version
```

On Debian/Ubuntu-based systems, you may also need to install the following native audio library dependencies:

```bash
# Install required native library dependencies
sudo apt-get update
sudo apt-get install -y libopus0 libsodium23
```


If your version is below 2.38, you will need to upgrade your system or use a compatible runtime. (You can also use Docker, which is recommended.)

## Docker

Build a local image from this repository; these examples do not assume a published fork registry package:

```bash
docker build -t jmusicbot:2610.1.0 .
```

The container includes Java 25 and runs headless. **YouTube is unavailable in the Linux container** because the embedded yt-dlp/Deno tools support Windows x64 only. Other enabled sources remain usable.

### Quick Start

1. Create a persistent bot directory, or use your existing directory containing `config.txt`, `Playlists/`, and other bot files.
2. Follow [Setup](#setup) to create `config.txt`, set `discord.token` and `discord.owner`, and set `playback.audioSources.youtube = false`.
3. The container runs as UID/GID `10001:10001`; make the mounted directory readable and writable by that user. For example, on Linux:

   ```bash
   sudo chown -R 10001:10001 /path/to/musicbot
   ```

4. Start the locally built image:

   ```bash
   docker run --rm -it \
     --name jmusicbot \
     -v "/path/to/musicbot:/musicbot" \
     jmusicbot:2610.1.0
   ```

If no `config.txt` exists, the bot generates a default; edit it with the required fields before restarting. Mount `/musicbot` on every run to retain configuration, playlists, and settings.

### Docker Compose

From the repository root:

```bash
cp docker-compose.example.yml docker-compose.yml
# Edit the volume path in docker-compose.yml and prepare config.txt as above
docker compose up -d --build
```

The [Compose example](docker-compose.example.yml) builds `jmusicbot:2610.1.0` locally. `JAVA_OPTS` overrides the default ZGC and AlwaysPreTouch flags; include them alongside heap limits if desired (see [Performance Tuning](#performance-tuning)).

## Performance Tuning

For optimal audio quality with minimal stuttering, the following JVM and configuration options are recommended.

### JVM Flags (Recommended)

The bot works best with ZGC (Z Garbage Collector) which provides sub-millisecond pause times:

```bash
java -Dfile.encoding=UTF-8 \
     -XX:+UseZGC \
     -XX:+AlwaysPreTouch \
     -Dnogui=true \
     --enable-native-access=ALL-UNNAMED \
     -jar JMusicBot-*.jar
```

**Flag explanations:**
- `-Dfile.encoding=UTF-8`: Ensures non-English characters display correctly in Discord (required on Windows or older JDKs)
- `-XX:+UseZGC`: Sub-millisecond GC pauses (generational mode is default in JDK 24+)
- `-XX:+AlwaysPreTouch`: Pre-allocates memory at startup to avoid page faults
- `-Xms` / `-Xmx`: Optional; set heap size if you want to limit or fix memory (e.g. `-Xms256m -Xmx512m`)

The Docker image uses ZGC and AlwaysPreTouch by default. Set `JAVA_OPTS` to add heap limits or override:
```yaml
environment:
  - JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseZGC -XX:+AlwaysPreTouch
```

### Audio Buffer Configuration

The bot includes configurable audio buffers that protect against GC pauses. These can be tuned in `config.txt`:

```hocon
performance {
  # NAS buffer duration in ms (protects against JVM pauses up to this duration)
  nasBufferMs = 800
  
  # Lavaplayer frame buffer duration in ms (amount of decoded audio to buffer)
  frameBufferMs = 2000
}
```

Higher values provide more protection against stuttering but add latency. The defaults (800ms NAS, 2000ms frame buffer) should work well for most setups.

## Proxy Configuration

JMusicBot supports granular proxy configuration, allowing you to route specific components through a proxy while letting others connect directly. This is useful when you need to proxy audio traffic (e.g., to bypass regional restrictions) without affecting Discord API communication.

### Configuration

Add the following to your `config.txt`:

```hocon
proxy {
  # Proxy server hostname and port
  host = "127.0.0.1"
  port = 8080
  
  # Enable proxy for specific components
  lavaplayer = true   # Audio source requests (YouTube, SoundCloud, etc.)
  jda = false         # Discord API traffic
  github = false      # Version check requests
}
```

### Common Use Cases

**Route only audio traffic through proxy** (most common):
```hocon
proxy {
  host = "127.0.0.1"
  port = 18080
  lavaplayer = true
  jda = false
  github = false
}
```

**Route all traffic through proxy**:
```hocon
proxy {
  host = "proxy.example.com"
  port = 8080
  lavaplayer = true
  jda = true
  github = true
}
```

### Notes

- Leave `host` empty or `port` as 0 to disable proxy entirely
- Each component can be independently enabled/disabled
- HTTP proxies are supported (SOCKS proxies are not currently supported)

## Development Workflow

This project follows a **trunk-based development** workflow. The `master` branch is always releasable, and all work happens in short-lived branches:

- **`feature/<slug>`** - New features (e.g., `feature/new-player-ui`)
- **`fix/<slug>`** - Bug fixes (e.g., `fix/youtube-playback`)
- **`chore/<slug>`** - Maintenance tasks (e.g., `chore/update-deps`)
- **`deps/<slug>`** - Dependency experiments (e.g., `deps/update-yt-dlp`)
- **`release/<version>`** - Release stabilization (optional, e.g., `release/2610.1.0`)

Branch names are automatically validated by CI to ensure consistency. For detailed information about the development workflow, branch naming rules, and best practices, see [DEVELOPMENT_WORKFLOW.md](docs/DEVELOPMENT_WORKFLOW.md).

## Questions/Suggestions/Bug Reports
Please search this fork's [issues](https://github.com/kdm1jkm/MusicBot/issues) before opening a question, feature request, or reproducible bug report. Include your version, operating system, and relevant logs, with tokens and cookies removed. Upstream support services are not operated by this fork.

Libraries and tools that make this possible:
 - [**JDA**](https://github.com/discord-jda/JDA)
 - [**lavaplayer**](https://github.com/lavalink-devs/lavaplayer)
 - [**yt-dlp**](https://github.com/yt-dlp/yt-dlp) and its [**EJS scripts**](https://github.com/yt-dlp/ejs)
 - [**Deno**](https://github.com/denoland/deno)

## Editing
See [Development Workflow](docs/DEVELOPMENT_WORKFLOW.md) for contribution and build instructions. This fork preserves the original authors' attribution and the project's [Apache 2.0 License](LICENSE). Bundled third-party programs retain their own licenses; redistributing the JAR also requires preserving their notices and meeting applicable GPLv3+ corresponding-source obligations described [above](#bundled-youtube-playback-windows-x64).

# Development Workflow

This independent [kdm1jkm/MusicBot](https://github.com/kdm1jkm/MusicBot) fork follows a **trunk-based development** workflow. Its version line starts at **2610.1.0**, independently of upstream's 0.x releases.

Clone this fork for development; keep upstream separate when importing changes:

```bash
git clone https://github.com/kdm1jkm/MusicBot.git
cd MusicBot
git remote add upstream https://github.com/arif-banai/MusicBot.git
```

`origin` is the publication target for this fork. Open pull requests and issues at `kdm1jkm/MusicBot`; preserve upstream author and license attribution when integrating changes.

## Branch Strategy

### Trunk Branch: `master`

- **Always releasable** - The `master` branch should always be in a deployable state
- **Protected** - Direct pushes should be avoided; use pull requests
- **Main integration point** - All changes merge into `master`
- **Production-ready** - Code on `master` can be released at any time

### Short-Lived Branches

All feature work happens in short-lived branches that are merged back to `master` quickly (typically within days, not weeks).

#### Branch Types

1. **`feature/<slug>`** - New features and enhancements
   - Examples: `feature/new-player-ui`, `feature/playlist-import`
   - Use for: Adding new functionality, UI improvements, major enhancements

2. **`fix/<slug>`** - Bug fixes
   - Examples: `fix/youtube-playback`, `fix/memory-leak`
   - Use for: Fixing bugs, resolving issues, patching vulnerabilities

3. **`chore/<slug>`** - Maintenance tasks
   - Examples: `chore/update-dependencies`, `chore/refactor-audio-handler`
   - Use for: Code cleanup, refactoring, dependency updates, documentation

4. **`deps/<slug>`** - Dependency experiments
   - Examples: `deps/update-yt-dlp`, `deps/test-jda-6.4`
   - Use for: Testing dependency updates, experimenting with forks, evaluating new libraries

5. **`release/<version>`** - Release stabilization (optional)
   - Examples: `release/2610.1.0`, `release/2610.1.0-rc1`
   - Use for: Stabilizing a release, release candidates, hotfixes for specific versions
   - **Note**: Only create when you need to stabilize a release. Most releases can go directly from `master`

### Branch Naming Rules

- **Format**: `<type>/<descriptive-slug>`
- **Slug requirements**:
  - Lowercase letters, numbers, and hyphens only
  - Descriptive and concise (e.g., `new-player-ui`, not `ui` or `new-feature`)
  - No underscores or special characters
- **Examples**:
  - Valid: `feature/new-player-ui`
  - Valid: `fix/youtube-playback`
  - Valid: `chore/update-gradle-plugins`
  - Valid: `deps/test-lavaplayer-2.3`
  - Valid: `release/2610.1.0`
  - Invalid: `feature/newFeature` (uppercase)
  - Invalid: `fix/bug_123` (underscore)
  - Invalid: `new-feature` (missing type prefix)
  - Invalid: `feature/new feature` (spaces)

## Development Process

### 1. Starting Work

```bash
# Create a branch from master
git checkout master
git pull origin master
git checkout -b feature/my-new-feature

# Or for a bug fix
git checkout -b fix/bug-description
```

### 2. Making Changes

- Make small, focused commits
- Write clear commit messages
- Keep the branch up-to-date with `master`:
  ```bash
  git checkout master
  git pull origin master
  git checkout feature/my-new-feature
  git rebase master  # or git merge master
  ```

### 3. Testing Locally

- Use JDK 25 and the pinned Gradle 9.1.0 Wrapper; no global Gradle installation is required. On Windows, replace `./gradlew` with `.\gradlew.bat`.
- Full build: `./gradlew --no-daemon build` runs unit tests (`test`), integration tests (`integrationTest`), and the shaded JAR task (`shadowJar`). The artifact is `build/libs/JMusicBot-2610.1.0-All.jar`.
- Focused tasks: `./gradlew test`, `./gradlew integrationTest`, or `./gradlew shadowJar` (packaging only, without running tests).
- Coverage: `./gradlew jacocoTestReport jacocoIntegrationTestReport` writes `build/reports/jacoco/test/jacocoTestReport.xml` and `build/reports/jacoco/integrationTest/jacocoIntegrationTestReport.xml`.
- Docker-pruned packaging: `./gradlew shadowJar -Pdocker -x test -x integrationTest` excludes native dependencies not needed in the container. YouTube uses the same embedded youtube-source backend on supported platforms.
- YouTube uses the pinned `dev.lavalink.youtube:v2:1.18.2` Java dependency; packaging no longer downloads or embeds Windows yt-dlp/Deno executables.
- youtube-source 1.18.2 creates a separate HTTP client for embedded-player host flags. `ProxiedWebEmbeddedClient` keeps that auxiliary request on the same configured audio proxy; the regular source HTTP manager alone does not cover it.
- Test Docker build: `docker build -t jmusicbot:test .`
- Verify the bot works as expected

### 4. Creating a Pull Request

- Push your branch: `git push origin feature/my-new-feature`
- Create a PR targeting `master`
- CI validates branch naming, then runs the build and tests for pull requests targeting `master`.
- Wait for CI to pass and code review. Docker publication is separate from PR validation.

### 5. Merging

- Once approved and CI passes, merge the PR
- **Prefer squash merge** to keep history clean
- Delete the branch after merging (GitHub can do this automatically)

## CI/CD Workflows

### Branch Validation

The `validate-branch-naming.yml` workflow automatically validates branch names when a pull request is opened.

**What it checks:**
- Branch name matches allowed patterns
- Slug uses only lowercase, numbers, and hyphens
- Proper type prefix is used

### Build and Test

The `build-and-test.yml` workflow runs on pull requests targeting `master` and pushes to `master` (with path exclusions for documentation and repository metadata).

**What it does:**
- Compiles the project
- Runs unit and integration tests
- Generates code coverage reports
- Uploads coverage to Codecov

### Docker Publication

`docker-publish.yml` is configured to publish after successful `master` builds and on version tags (for example, `v2610.1.0`). `publish-preview-image.yml` can be dispatched manually for a selected ref. These workflows require package-write permissions and successful registry publication; their presence does not mean a public fork image already exists.

Use a local build unless publication has been confirmed:

```bash
docker build -t jmusicbot:2610.1.0 .
```

See [README Docker setup](../README.md#docker) and [docker-compose.example.yml](../docker-compose.example.yml). Linux containers cannot run the Windows x64 bundled YouTube backend.

## Best Practices

### Keep Branches Short-Lived

- **Goal**: Merge within days, not weeks
- **Why**: Reduces merge conflicts, keeps code fresh, enables faster feedback
- **If stuck**: Break work into smaller PRs

### Keep `master` Releasable

- **Never** push broken code to `master`
- **Always** ensure tests pass before merging
- **Use** feature flags if needed for incomplete features
- **Consider** draft PRs for work-in-progress

### Small, Focused PRs

- **One feature/fix per PR** when possible
- **Easier to review** and understand
- **Faster to merge** and deploy
- **Less risk** of conflicts

### Regular Integration

- **Rebase or merge** `master` into your branch regularly
- **Run tests** locally before pushing
- **Fix CI failures** promptly

### Clear Commit Messages

- **Format**: `<type>: <description>`
- **Types**: `feat`, `fix`, `chore`, `docs`, `refactor`, `test`
- **Example**: `feat: add playlist import from YouTube`
- **Why**: Makes history readable and enables automated changelogs

## Release Process

### Standard Release (from master)

1. **Ensure `master` is stable**
   - All tests passing
   - No known critical bugs
   - Documentation updated

2. **Update version in `gradle.properties`** (manual release path; **Make Release** performs this itself)
   - Set `version=2610.1.0` (or the intended release version), or run `./gradlew setVersion -PreleaseVersion=2610.1.0`.
   - Read the current version with `./gradlew -q printVersion`.
   - Commit: `git commit -am "chore: bump version to 2610.1.0"`

3. **For a manual release, build and create the tag** (skip this step when using **Make Release**)
   ```bash
   ./gradlew --no-daemon build
   git tag v2610.1.0
   git push origin v2610.1.0
   ```

4. **Publish the release**
   - Either run **Make Release** on `master` with version `2610.1.0` (it updates the version, builds, and prepares a draft release/tag), or create a release manually from the tag above. Use one path, not both for the same version.
   - Attach `build/libs/JMusicBot-2610.1.0-All.jar`; retain the `All` classifier in the published filename.
   - Review and publish the draft at [this fork's releases](https://github.com/kdm1jkm/MusicBot/releases).
   - Preserve applicable dependency licenses and notices; youtube-source is included as a Java dependency, not a separate executable distribution.

5. **Check Docker publication separately**, if enabled; do not advertise a registry tag until it is successfully published and accessible.

### Release Branch (for stabilization)

Only use if you need to stabilize a release while continuing development:

1. **Create release branch**
   ```bash
   git checkout -b release/2610.1.0
   git push origin release/2610.1.0
   ```

2. **Stabilize on release branch**
   - Fix critical bugs
   - Run extensive testing
   - Cherry-pick fixes from `master` if needed

3. **Tag from release branch**
   ```bash
   git tag v2610.1.0
   git push origin v2610.1.0
   ```

4. **Merge back to master** (if needed)
   - Merge any fixes back to `master`
   - Delete release branch after release

## FAQ

### Q: Can I push directly to master?

**A**: Not recommended. Use pull requests for all changes to ensure:
- Code review
- CI validation
- Better history tracking

### Q: How long should branches live?

**A**: Ideally less than a week. If work takes longer, consider:
- Breaking into smaller PRs
- Using feature flags
- Creating a release branch if needed

### Q: What if I need to experiment?

**A**: Use `deps/<slug>` branches for dependency experiments, or create a personal fork for major experiments.

### Q: Can I use different branch names?

**A**: The CI will reject branches that don't match the allowed patterns. This ensures consistency and makes it easier to understand what each branch is for.

### Q: What about hotfixes?

**A**: For urgent production fixes:
1. Create `fix/<description>` branch from `master`
2. Fix the issue
3. Create PR and merge quickly
4. Tag the next fork release version and publish its release notes and `All.jar` artifact.

## Summary

- **Trunk**: `master` is always releasable
- **Branches**: Short-lived, type-prefixed (`feature/`, `fix/`, `chore/`, `deps/`, `release/`)
- **Process**: Branch -> Develop -> Test -> PR -> Merge -> Release
- **CI**: Automatic validation and testing; Docker publication requires separate successful registry access
- **Goal**: Fast, safe, continuous integration and deployment

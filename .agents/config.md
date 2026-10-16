# Workflow configuration

Repository-local workflow configuration for `kdm1jkm/MusicBot`. Paths are repository-relative unless linked otherwise.

## Repository conventions (inherited from `docs/DEVELOPMENT_WORKFLOW.md` & CI)

- **Default branch (trunk)**: `master` (base all new feature/fix/chore/deps/release branches on `master`, not `main`).
- **Required PR branch naming (`validate-branch-naming.yml`)**:
  - `feature/<slug>` (new features and enhancements)
  - `fix/<slug>` (bug fixes)
  - `chore/<slug>` (maintenance, refactoring, docs, workflow)
  - `deps/<slug>` (dependency updates/experiments)
  - `release/<version>` (e.g., `release/2610.1.0` or `release/2610.1.0-rc1`)
  - `<slug>` MUST use lowercase letters, numbers, and hyphens only (`[a-z0-9-]+`).
- **Commit convention**: `<type>: <description>` in English (`feat`, `fix`, `chore`, `docs`, `refactor`, `test`).

## State labels (fixed)

| Label | Meaning |
| --- | --- |
| `agent:working` | Claimed by an active agent session |
| `agent:passed` | Objective checks and independent agent quality review passed; PR awaits human merge |
| `agent:blocked` | Requires human decision or external action |

## Priority labels

Descending: `P0`, `P1`, `P2`, then unlabeled. If multiple priority labels exist, use the highest. FIFO (`created_at` ascending, number ascending as tie-break) applies within a tier.

## PR merge strategy

Confirmed: `squash` (inherited from `docs/DEVELOPMENT_WORKFLOW.md`: "Prefer squash merge to keep history clean"; allowed by repository settings). Alternatives allowed by repo: `rebase`, `merge`. Humans merge; PASS does not authorize an agent merge.

## Bounded self-repair limit

`K = 3` repair iterations per session across local failures, CI failures, and review findings. Initial verification is not a repair. Do not reset the budget on tool, skill, or phase transitions. At exhaustion record evidence, release the claim, hand off, and stop.

## Language policy

Confirmed: Korean (`ko`) for human-facing output; English (`en`) following repository convention for commit messages.

| Output | Language |
| --- | --- |
| Replies to the user, questions, reports | Korean |
| Issue/PR comments, Canonical Spec Snapshot content, PR title/body | Korean |
| `.agents/tasks/<issue-number>.md` content | Korean |
| Commit messages | English following target repository convention (`<type>: <description>`) |

Fixed regardless of language: snapshot and task-file Markdown headings defined in the [workflow model](reference/workflow-model.md) (for example `## Canonical Spec Snapshot`, `### Acceptance Criteria`), label names, branch names, file paths, commands, code identifiers, and quoted logs/errors. Agents locate artifacts by these exact headings; translating them breaks discovery. Skill contracts stay in English for stable agent interpretation.

## Task file retention policy

Confirmed: `keep-in-git`.

- `keep-in-git`: commit `.agents/tasks/<issue-number>.md` for portable handoff.
- Directory marker: `.agents/tasks/.gitkeep`.

## Verification commands

Prerequisites: JDK 25 (`JavaLanguageVersion.of(25)`) and the pinned Gradle 9.1.0 Wrapper (`./gradlew` on Unix/Git Bash, `.\gradlew.bat` on Windows PowerShell/CMD). Packaging (`bundleYoutubeTools` / `shadowJar`) validates pinned Windows x64 `yt-dlp.exe` and `deno.zip` SHA-256 checksums.

| Check | Target command / applicability |
| --- | --- |
| test | `./gradlew --no-daemon test integrationTest` (or `.\gradlew.bat --no-daemon test integrationTest` on Windows; unit tests via `test`, integration tests via `integrationTest`, opt-in E2E via `./gradlew --no-daemon e2eTest`) |
| lint | `./gradlew --no-daemon checkstyleMain checkstyleTest -Pcheckstyle` and `./gradlew --no-daemon spotbugsMain -Pspotbugs` (opt-in Gradle property checks defined in `build.gradle`; not required by default PR CI, while `JavaCompile` enforces `-Xlint:deprecation`) |
| typecheck | `Not applicable` as a standalone tool: Java 25 static type checking is enforced during compilation via `./gradlew --no-daemon compileJava compileTestJava` (included in `test` and `build`) |
| build | `./gradlew --no-daemon build` (or `.\gradlew.bat --no-daemon build`; runs `compileJava`, `bundleYoutubeTools`, `test`, `integrationTest`, `jar`, `shadowJar` producing `build/libs/JMusicBot-<version>-All.jar`) |

Required CI checks on pull requests targeting `master`:
- `.github/workflows/validate-branch-naming.yml`: `Validate Branch Naming Convention` (when PR is opened)
- `.github/workflows/build-and-test.yml`:
  - `Build and Test` (`./gradlew --no-daemon build jacocoTestReport jacocoIntegrationTestReport` on `ubuntu-latest`)
  - `Windows x64 Bundled Tools` (`.\gradlew.bat --no-daemon build` + embedded `yt-dlp`/`deno` version smoke test from shaded `All` JAR on `windows-latest`)
- Note: `.github/workflows/build-and-test.yml` has `paths-ignore` on `push` to `master` for documentation/metadata paths, and `.circleci/config.yml` defines a legacy CircleCI `build` job (`./gradlew --no-daemon build`).

## MCP extensions

Approved project MCPs: none. Standard file tools, Git, and authenticated GitHub CLI (`gh`) / native tools are sufficient. Do not install or edit global MCP configuration without approval.

## Initialization commit policy

Confirmed: create an atomic commit staging only workflow initialization paths (`AGENTS.md`, `CLAUDE.md`, `.agents/`) using the repository commit convention (`chore: initialize agent workflow`). Never include unrelated user files (such as untracked `.jmusicbot.lock`).

## References

- [Workflow model](reference/workflow-model.md)
- [Compatibility](reference/compatibility.md)
- [Development workflow](../docs/DEVELOPMENT_WORKFLOW.md)
- [Architecture](../docs/ARCHITECTURE.md)

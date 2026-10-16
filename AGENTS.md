# Repository workflow index

Repository-native GitHub Issue/PR workflow for `kdm1jkm/MusicBot`. Read the matching Task Skill and its linked Capability Skills.

Language: follow the Language policy in [config](.agents/config.md) (Korean for replies, Issue/PR comments, spec content, PR bodies, and task files; English `<type>: <description>` for commit messages). Keep workflow headings, labels, paths, and commands verbatim.

## Entry points and Task Skills

| User intent | Task Skill | Purpose |
| --- | --- | --- |
| 다음 작업 진행해 / 할 수 있는 작업 보여줘 / 이어서 진행해 / 이 Repository 작업 시작해; next task / resume | [select-next-task](.agents/skills/select-next-task/SKILL.md) | Discover candidates; wait for selection |
| 상태 요약해줘 / 현재 작업 큐 보여줘 / 진행 상황 알려줘; status | [workflow-status](.agents/skills/workflow-status/SKILL.md) | Read-only queue and progress |
| 이슈 분석해줘 / 요구사항 정리해줘 / 큰 작업 쪼개줘; analyze issue | [triage-and-spec](.agents/skills/triage-and-spec/SKILL.md) | Mature requirements and split issues |
| 이슈 구현해줘 / 작업 진행해 / 기능 개발/버그 수정; implement issue | [implement-issue](.agents/skills/implement-issue/SKILL.md) | Implement a selected issue end to end |
| PR 리뷰해줘 / PR 피드백 반영해줘 / 검증 및 PASS 판정; review PR | [review-and-pass-pr](.agents/skills/review-and-pass-pr/SKILL.md) | Improve PR and evaluate PASS |
| 워크플로 초기화해줘; initialize workflow | [init-workflow](.agents/skills/init-workflow/SKILL.md) | Install into a target repository |

## Capability Skills

| Skill | Purpose |
| --- | --- |
| [github-state-and-labels](.agents/skills/github-state-and-labels/SKILL.md) | GitHub claims, labels, dependencies |
| [spec-management](.agents/skills/spec-management/SKILL.md) | Canonical specs and controlled evolution |
| [git-and-worktree](.agents/skills/git-and-worktree/SKILL.md) | Safe branches, worktrees, atomic commits |
| [tdd-and-verification](.agents/skills/tdd-and-verification/SKILL.md) | TDD, bounded repair, independent PASS review |
| [task-file-and-handoff](.agents/skills/task-file-and-handoff/SKILL.md) | Task files and cross-session handoff |

## Repository paths and project conventions

- [Workflow settings](.agents/config.md)
- [Workflow model](.agents/reference/workflow-model.md)
- [Agent compatibility](.agents/reference/compatibility.md)
- Task and handoff files: `.agents/tasks/<issue-number>.md`
- Project documentation:
  - [Development workflow](docs/DEVELOPMENT_WORKFLOW.md) — trunk branch `master`, required PR branch naming (`feature/<slug>`, `fix/<slug>`, `chore/<slug>`, `deps/<slug>`, `release/<version>`), commit convention (`<type>: <description>`), Gradle 9.1.0 / JDK 25 build and release process
  - [Architecture](docs/ARCHITECTURE.md) — historical architectural reference
  - [README (EN)](README.md) / [README (KO)](README.ko.md)

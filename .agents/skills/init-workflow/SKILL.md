---
name: init-workflow
description: Initialize repo-local workflow using stack detection, selective merge, and batched policy decisions.
layer: task
---

# Initialize workflow

Read the template's [configuration](../../config.md), [workflow model](../../reference/workflow-model.md), [compatibility](../../reference/compatibility.md), and [Git safety](../git-and-worktree/SKILL.md). Source paths refer to the template checkout until installation, then to the target. This is an initialization task, not authorization to start application work.

## 1. Analyze without overwriting

- MUST identify source/template and target roots separately. Obtain the user-provided template URL or local source path, inspect its contents, and use a temporary checkout outside the target if needed. MUST NOT clone over the target, copy template Git history, or replace its README with the template's README.
- MUST inspect target tree, existing `AGENTS.md`, `CLAUDE.md`, `.agents/`, `.claude/`, `.omp/`, project docs, CI workflows, hooks including `core.hooksPath`, manifests, Git status/branches/worktrees, remotes, commit conventions, and the language predominantly used in recent Issues/PRs/commits. Preserve user changes and existing security rules. Never execute untrusted hooks/scripts merely for detection.
- MUST identify the stack from actual manifests (such as `package.json`, `pyproject.toml`, `Cargo.toml`, `go.mod`, `Makefile`) and CI, including monorepo working directories. Record exact existing test/lint/typecheck/build commands, prerequisites, required CI checks, and explicitly inapplicable checks. Do not invent commands from stack names.
- MUST inspect existing GitHub labels and allowed merge policies if accessible (`gh label list`, repository settings/native equivalent); use complete pagination. If no remote/auth exists, complete reachable local installation and report remote work as pending, not done.

## 2. Capability and MCP assessment

MUST assess existing file/Git/GitHub tool capability first. Default to no MCP extensions. For a genuinely missing stack-essential capability, propose minimal candidates with benefit, permission/data exposure, credential needs, maintenance cost, and CLI alternative. MUST NOT install MCPs, write secrets, or edit global settings without approval. MAY use already-approved project tools without duplicating them.

## 3. Batch unresolved decisions

MUST present one batched question set, skipping decisions already unambiguously established by repository policy or explicit user instruction. Record the source of inherited decisions. Defaults are proposals, not silent consent.

| Decision | Options and trade-offs |
| --- | --- |
| Priority labels | `priority:high/medium/low` (descriptive default) vs existing tiers such as `P0/P1/P2` (continuity); confirm order and unlabeled placement |
| Final PR merge strategy | `squash` (one commit), `rebase` (linear individual commits), `merge` (preserve topology); constrain to allowed repo settings |
| Initial MCP extensions | none (minimal access/dependency) vs identified essential candidates (extra capability, credentials and permissions); ask only if candidates exist |
| Task file retention | `keep-in-git` (portable history), `clean-before-pass` (clean diff, promote durable context), `gitignored-local` (less repository noise, GitHub mirror required for cross-worktree handoff) |
| Conflict resolution | preserve existing rules and link/merge non-conflicting additions vs explicitly approved replacement; list exact conflicting instructions, skill paths, CI/hooks and consequences; never silently override |
| Initialization commit policy | leave unstaged (user inspects), stage only initialization paths (user commits), create atomic commit using existing convention or an agreed message; never include unrelated changes |
| Language policy | Korean default (user-facing replies, Issue/PR comments, snapshot content, PR body, task files) vs the language consistently used by existing target Issues/PRs/commits (continuity for other contributors) vs English (wider audience); headings, labels, paths and commands always stay as defined |

MUST await required decisions before conflicting mutations. No conflict means report none, not manufacture a question. Never weaken CI/hooks to install a workflow.

## 4. Selective installation

1. MUST copy canonical flat skills and references into target `.agents/`, preserving unrelated files. For a colliding skill or rule, compare semantics, deduplicate into one source, and apply approved conflict resolution; do not overwrite just because names match. No runtime adapter or template-sync dependency may be introduced.
2. MUST write target `.agents/config.md` with confirmed/inherited policies, fixed state labels, ordered priority labels, K, language policy, retention, exact verification commands/applicability, approved MCPs, and commit policy. Create the task directory marker if absent. For local-only retention ignore only numeric task Markdown files, not all `.agents/` or all skills.
3. MUST create/merge a minimal `AGENTS.md` index linking Task and Capability Skills, config, references, task path and existing project docs. Preserve existing project-specific instructions; moving long prose to a linked existing/approved project document requires preserving its meaning, not deleting it.
4. MUST create a one-line `CLAUDE.md` import `@AGENTS.md` if absent; otherwise append/deduplicate the import while preserving project instructions. Native Claude skill discovery bridges are optional and require an actual need; linked reading is sufficient. MUST NOT change global agent configuration.
5. MUST preserve existing CI and hooks unless the user explicitly approved a necessary change. Existing verification policy remains authoritative; record true incompatibilities instead of bypassing checks.
6. MUST ensure fixed state and chosen priority labels exist remotely when authorized/access is available. Use `gh label create <name> --color <hex> --description <meaning>` for missing labels, or `--force` only for approved metadata updates after inspection. Do not overwrite existing label semantics/colors merely to match template defaults. Record inaccessible remote steps precisely.

## 5. Verify and finalize

MUST check all index/skill/reference links, flat skill depth, frontmatter names/descriptions/layers, Claude import and preserved target rules. MUST exercise the installed status/selection routes read-only using available live data or clearly labeled fixtures; verify user-selection pause and no mutation. Follow compatibility checks with available agents; report unavailable agents as unexercised.

SHOULD present a concise before/after policy summary so the user can inspect inherited rules and approved changes without rereading every installed skill.

MUST run target verification appropriate to actual changed files and inspect `git status`. Stage/commit only according to the selected commit policy. Report installed/merged/preserved files, detected commands, policies, remote labels, compatibility evidence and exact outstanding access prerequisites. Remove only initialization-owned temporary checkouts when safe. STOP; do not select or implement a new Issue automatically.

## Self-Check

- Stack/commands, existing agent settings, CI/hooks and labels inspected without overwriting?
- Seven decision categories (including language) resolved or explicitly inherited/not applicable, MCP access approved?
- Selective installation preserves target rules, all paths resolve, commit policy honored?
- Live vs fixture and unavailable agents/remotes honestly reported; stopped after initialization?

---
name: task-file-and-handoff
description: Maintain one Markdown task file per Issue and perform truth-first portable handoff.
layer: capability
---

# Task file and handoff

Read [config](../../config.md), [task format](../../reference/workflow-model.md), and [state lifecycle](../github-state-and-labels/SKILL.md).

## Resume truth hierarchy

MUST evaluate context in this strict priority order:

1. Original user intent.
2. Latest Canonical Spec Snapshot plus subsequent Issue/PR comments (or clear original body if no snapshot needed).
3. Actual repository code and Git state: status, diff, log, branch/head.
4. Objective verification and CI results tied to their heads.
5. Previous agent task file and handoff.

MUST resolve conflicts in favor of higher-priority truth, not blindly follow stale plans or green results for older code. MUST ask for approval when reconciliation would change intended scope.

## Single task file

MUST store agent task state at repository-relative `.agents/tasks/<issue-number>.md`, never in project `docs/`. Use free-form Markdown; no YAML schema required. SHOULD use the model's recommended headings verbatim and write their content in the configured language. Include Issue/spec/PR URLs, branch/head, current status, completed work, verification commands/outcomes, decisions, unresolved blockers, repair attempts used, and exact next action.

MAY discard/rewrite the previous `Implementation Plan (Disposable)` as evidence changes, while preserving traceable constraints and key decisions. Domain/architecture knowledge belongs in project documentation or durable Issue/PR discussion, not only disposable task files.

## End every authorized task/session

1. MUST update the file for PASS, incomplete handoff, or blocked work. Include independently observed facts and explicit unknowns, not implied success.
2. MUST apply configured retention: keep/commit for `keep-in-git`; for `clean-before-pass`, promote durable decisions and essential evidence to docs/PR, then remove the file only on successful completion before final checks; for `gitignored-local`, mirror essential context to an Issue/PR comment so other worktrees/agents can resume. On incomplete work preserve the file for all policies.
3. MUST synchronize reachable Git state under commit/push permissions and record any failed/unavailable synchronization. Never commit unrelated user changes or invent successful pushes.
4. MUST post final evidence, blocker/action or handoff to the Issue/PR, transition labels, and remove this session's `agent:working` on both Issue and PR. Apply passed only with dual-gate evidence; blocked only with documented external/human action.
5. MUST report what changed, exact verification outcomes, PR/Issue references, and remaining action to the user in the configured language, then STOP. MUST NOT auto-start another task or merge after PASS. SHOULD make the handoff sufficient without chat history.

## Self-Check

- One Issue-linked task file; current code/spec outrank disposable plans?
- Retention and GitHub mirror support the next agent; sync failures explicit?
- Labels released, next human/external action clear, STOP honored?

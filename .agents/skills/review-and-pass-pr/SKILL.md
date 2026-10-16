---
name: review-and-pass-pr
description: Resume or improve an unpassed PR, resolve feedback, obtain independent review and judge PASS.
layer: task
---

# Review and PASS PR

Read [GitHub state](../github-state-and-labels/SKILL.md), [spec management](../spec-management/SKILL.md), [Git/worktrees](../git-and-worktree/SKILL.md), [TDD/verification](../tdd-and-verification/SKILL.md), [handoff](../task-file-and-handoff/SKILL.md), [model](../../reference/workflow-model.md), and [config](../../config.md).

## 1. Claim and load context

MUST verify live PR and linked Issue eligibility, labels, ownership and dependencies before claiming both with `agent:working`. A continuation of already-authorized implementation keeps its own claim and K counter. A PR without an Issue must be linked through authorized triage before active work. Read original intent/body, latest Canonical Spec Snapshot plus all subsequent Issue comments, PR discussion and review threads, CI check status, actual branch/diff against main/default branch, and finally the task file.

If `agent:passed` is still present, MUST NOT remove it or start rework merely because feedback appeared. Report that a human must explicitly remove PASS. Once a human removes it, the PR re-enters the Incomplete PR category (before actionable Issues, sorted by priority then FIFO) if otherwise eligible; selection and live claim still apply.

## 2. Synchronize spec and feedback

MUST link important Issue comments added since PR opening/update and summarize requirement/clarification impact in the PR. MUST distinguish approved scope from suggestions. Ask approval before material changes; never weaken original intent to fit implementation. MAY refactor prior agent code to address real correctness/maintainability findings within scope.

## 3. Repair, self-review, prepare final head

MUST use TDD and existing verification tools to resolve feedback, failing checks and regressions. MUST track every repair against the same session K, never reset it on review entry. SHOULD perform self-review of the entire diff after fixes. At K unresolved repairs, stop and hand off per step 5.

MUST update task/handoff state and apply retention before finalizing commits. For clean-before-pass promote durable decisions/evidence and remove the task file; for local-only mirror essential state to GitHub. Push final commits, then verify exact final pushed head. If new edits are needed, refresh affected objective checks and independent review. Do not mark old evidence as validating a changed head.

## 4. Independent quality review and PASS gate

MUST obtain a distinct independent agent/reviewer session, supplied with current spec, final head/diff and check evidence. Record reviewer identity/context, reviewed head, findings, resolutions and final judgment in PR/task evidence. Implementer's self-review alone is insufficient; if no independent reviewer exists, hand off without PASS.

MUST check every gate item:

1. Every current Spec Acceptance Criterion satisfied by observable behavior.
2. Required behavioral/regression tests exist and pass; appropriate changed-path smoke evidence recorded.
3. Applicable local lint/typecheck/build checks pass, with explicit reasons for genuinely inapplicable checks.
4. Required CI checks pass for the exact final pushed head. Pending/missing/unavailable required checks do not pass.
5. Independent quality judgment confirms correctness, readability, maintainability.
6. No unrelated diff noise, debug scaffolds, accidental files or user changes included.
7. Security/regression risks reviewed and resolved; no outstanding required fixes or unapproved scope changes.

## 5. Transition, report, STOP

- On BOTH objective success AND independent review approval: MUST update PR description/evidence with final head, commands/results, CI links and reviewer record; add `agent:passed` to PR; remove own `agent:working` on PR and Issue; confirm remote label state; report PASS awaiting human merge; STOP. MUST NOT merge automatically.
- On unresolved failures after K, or unavailable reviewer/checks/access: MUST record findings, attempted repairs, exact remaining actions and heads in task/PR handoff; preserve task file if completion cleanup did not finish; remove own working labels; leave PR unpassed. If external/human action is required, apply `agent:blocked` with reason/action in file and comment. Report and STOP.
- MUST NOT auto-start the next task on either outcome. Retention cleanup does not authorize deletion of unrelated files or suppression of unresolved findings.

## Self-Check

- Current Issue/PR feedback and true final head considered, human-removed PASS handled correctly?
- Same session K respected; objective checks AND independent quality review passed for final head?
- PR evidence, retention and both working-label releases complete; human merge only; STOP?

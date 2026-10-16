---
name: implement-issue
description: Implement a selected Issue through spec, TDD, atomic commits, PR, independent review and PASS or handoff.
layer: task
---

# Implement issue

Read all capabilities before work: [GitHub state](../github-state-and-labels/SKILL.md), [spec management](../spec-management/SKILL.md), [Git/worktrees](../git-and-worktree/SKILL.md), [TDD/verification](../tdd-and-verification/SKILL.md), [task/handoff](../task-file-and-handoff/SKILL.md). Finish through [review-and-pass-pr](../review-and-pass-pr/SKILL.md). See [model](../../reference/workflow-model.md) and [config](../../config.md).

## 1. Pre-flight and claim

MUST have user-selected Issue scope; for an idea without an Issue, establish/create a linked Issue first. Re-fetch Issue/linked PR state, labels, dependencies and ownership; claim the Issue and existing PR through the state capability. MUST NOT bypass another session's claim, blocked/passed state, or open prerequisites. If an open PR already exists, resume its logical branch rather than create duplicate work.

## 2. Reconstruct context

MUST read original user intent, current spec (latest `## Canonical Spec Snapshot` plus subsequent Issue/PR comments or clear original body), actual code/Git, checks/CI, then task file. If spec is materially ambiguous, execute [triage-and-spec](../triage-and-spec/SKILL.md) first. Controlled minor refinements are allowed; scope/core changes require approval. Oversized work requires approved splitting before implementation.

## 3. Prepare branch and plan

MUST inspect working tree/worktrees, protect user changes, and create new work from main/default branch or safely sync the existing PR branch. One logical branch per PR; no stacked PR default. Initialize/update `.agents/tasks/<issue-number>.md` with authoritative references, branch/head, disposable plan and current status. MAY revise the plan as code evidence changes without rewriting approved intent.

## 4. TDD and atomic implementation

MUST use Red → Green → Refactor and existing repository tools to satisfy Acceptance Criteria. Record failing-before/passing-after behavior when practical; exercise changed runtime paths. MUST make atomic commits of related changes under repository commit policy. MUST NOT weaken requirements/tests or include unrelated files. SHOULD self-review each coherent change for correctness and scope.

## 5. Verify and bound repair

MUST run applicable test/lint/typecheck/build commands and record actual outcomes. MAY repair up to K iterations across this session, including later CI/review fixes. At unresolved K exhaustion, MUST record root cause, attempts, remaining evidence and next action, synchronize reachable branch/PR state, remove own working claims, report and STOP. A human/external blocker additionally requires blocked label plus action comment. Never claim PASS for unavailable checks.

## 6. Create or synchronize PR

MUST push the intended branch and create/update the PR with `Closes #<issue-number>` (or qualified cross-repository closing reference when appropriate), summary, key design decisions, verification results and honest remaining issues/limitations; write the PR title/body in the configured language. MUST link important new Issue comments and summarize their impact. Claim a newly created PR with this session's working label. Failed push/PR creation is recorded as incomplete remote state, not success. Draft PRs may preserve incomplete handoff but are not actionable queue candidates until explicitly made ready.

## 7. Review and stop

MUST self-review the final diff, then run [review-and-pass-pr](../review-and-pass-pr/SKILL.md) without resetting ownership or K. Independent review and final-head CI are both mandatory. Apply retention cleanup before final checks/review. On PASS add passed only to PR, release working on Issue and PR, report evidence for human merge, and STOP. Otherwise leave an actionable handoff or explicit blocked state and STOP. MUST NOT auto-merge or start another Issue after this one.

## Self-Check

- Selected, eligible Issue and current spec govern the actual branch/code?
- TDD, smoke evidence, atomic commits and repair count recorded?
- PR linked, final head independently reviewed and objectively verified, or honest handoff?
- Retention, labels, user report and mandatory STOP completed?

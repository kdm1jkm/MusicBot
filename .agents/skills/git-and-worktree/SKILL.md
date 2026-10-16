---
name: git-and-worktree
description: Inspect and safely adapt Git branches, existing worktrees, and atomic commit policy.
layer: capability
---

# Git and worktrees

Read [configuration](../../config.md) and [workflow model](../../reference/workflow-model.md).

## Inspect before mutation

- MUST inspect `git status`, `git branch`, and `git worktree list` before edits, and relevant `git diff` / `git log` to understand actual work. Identify repository root, current branch/head, remotes, default branch, and user changes.
- MUST adapt to user-created, IDE-managed, or agent-created worktrees. MUST NOT reset, overwrite, stash, delete, or commit unrelated user changes without explicit authorization. If safe isolation is impossible, stop with an exact blocker.

## One logical PR branch

1. MUST start new PR work from current `main` or repository default branch after a safe fetch/sync; do not branch from another feature PR. Reuse the existing logical PR branch when resuming, not create a duplicate PR.
2. MUST NOT require the same local branch checked out simultaneously in multiple worktrees. If occupied, use the owning worktree when safe, or a detached/worktree-specific checkout of the PR head and explicit remote refspec. MUST verify the intended remote head before push and avoid losing concurrent updates.
3. SHOULD use descriptive Issue-linked branch names. MAY create a worktree if helpful; no worktree manager or daemon is required.
4. MUST create atomic commits of related changes, staging explicit paths/hunks and verifying the staged diff; include regression tests with their fix. MUST follow existing commit conventions and authorized commit policy.
5. MUST NOT rewrite published history unless explicitly required/authorized by repository policy. Prefer safe synchronization, and stop on unexpected remote divergence rather than force pushing.
6. MUST push the actual logical PR branch and record final commit and remote result in handoff. If push fails, report local/remote heads and leave work unpassed; never claim remote synchronization.

## Merge and stop

MUST consult config for final merge strategy (`squash`, `rebase`, `merge`) and keep the PR compatible with repository policy. PASS only awaits human merge; MUST NOT merge or begin the next task automatically. SHOULD preserve unrelated worktrees and branches; task completion does not authorize cleanup of user resources.

## Self-Check

- Working state inspected and unrelated changes preserved?
- Default-branch base / existing PR branch correct, no double checkout dependency?
- Atomic commits follow policy; pushed head and human merge strategy recorded?

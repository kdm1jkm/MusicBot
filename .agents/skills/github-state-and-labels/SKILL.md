---
name: github-state-and-labels
description: Manage GitHub claims, state labels, priority, dependencies, and safe release.
layer: capability
---

# GitHub state and labels

Read [workflow model](../../reference/workflow-model.md) and [configuration](../../config.md). This capability is shared by discovery, triage, implementation, and review.

## Read and classify

1. MUST determine the correct repository and authenticated permissions before remote operations. Use `gh repo view`, `gh auth status`, or equivalent native tools; never print credentials.
2. MUST fetch complete open Issues/PRs, labels, timestamps, linked relationships, comments, review threads, and checks with pagination. `gh issue list` / `gh pr list` default limits are not a complete queue. SHOULD use `gh api --paginate` or paginated GraphQL/native tools where needed.
3. MUST read dependencies from the preserved Issue body and latest spec plus subsequent approved changes. Resolve `Depends on #N` / `Blocked by #N`; every prerequisite MUST be CLOSED. Unknown state, missing references, or cycles are non-actionable. For a PR, check its linked Issue too.
4. MUST exclude items bearing `agent:working`, `agent:blocked`, or `agent:passed`, draft/closed PRs, and unmet prerequisites from actionable candidates. MUST exclude Issues already represented by an open linked PR. Use configured priority labels then FIFO within PR/Issue categories; see the model for precise ordering.

## Claim protocol

1. MUST re-fetch live Issue and linked PR state immediately before claiming selected work. A cached queue is not authorization. Explicit selection does not bypass dependency or ownership checks.
2. MUST ask the user before reclaiming another session's possibly stale `agent:working`. Age or inactivity MAY be shown as evidence, never treated as permission. MUST NOT remove another agent's live claim.
3. MUST publish session/owner, timestamp, and intended work in an Issue/PR claim comment written in the configured language, then add `agent:working` to the Issue and any linked PR (`gh issue edit` / `gh pr edit --add-label` or equivalent). A linked PR created later MUST also receive this session's claim.
4. MUST re-fetch labels/comments after claiming to detect races. GitHub label updates are not compare-and-swap locks. If another session also claimed, stop and coordinate; on partial failure release only this session's claims and record the failure. Do not continue claiming success without remote confirmation.
5. A continuing authorized session MAY retain its own claim across skills; MUST NOT mistake its own claim for stale third-party ownership or reacquire it as a new task.

## Transitions

- MUST remove this session's `agent:working` from Issue and PR on authorized handoff, completion, or transition to `agent:blocked` / `agent:passed`.
- MUST record blocker reason and exact human/external action in the task file and an Issue/PR comment when applying `agent:blocked`. Clear blocked state only after its reason is resolved and work is authorized.
- MUST NOT remove `agent:passed` merely because a comment arrives. A human explicitly removes it for rework. Its absence makes a PR eligible again only if all other eligibility checks hold.
- MUST add `agent:passed` only to a PR after the dual PASS gate; MUST NOT mark the Issue passed and strand it in a filtered state.
- SHOULD keep unrelated labels, assignees, milestones, and original Issue body intact. MAY create missing configured labels during authorized initialization; do not repurpose existing labels without checking their meaning.

## Self-Check

- Complete live data and prerequisite states fetched? User selection and ownership confirmed?
- Claim race/partial failure handled without overriding another session?
- Working labels released on both records; PASS/blocker transitions backed by evidence?

---
name: select-next-task
description: Discover category-ranked PR and Issue candidates and wait for explicit user selection.
layer: task
---

# Select next task

Read [GitHub state](../github-state-and-labels/SKILL.md), [workflow model](../../reference/workflow-model.md), and [config](../../config.md).

## Discover, do not start

1. MUST fetch complete live PR/Issue data and dependencies before ranking; follow pagination and link detection. Unknown access MUST be reported, not replaced by a plausible queue.
2. MUST filter out `agent:working`, `agent:blocked`, `agent:passed`, unmet `Depends on #N` / `Blocked by #N`, closed and draft PRs, and Issues with any open linked PR. For PRs also verify linked Issue state/prerequisites; unlinked PRs need triage before action. A coordinating parent with unfinished split children is not a ready implementation item.
3. MUST rank strictly: **Incomplete PR > Actionable Issue**, then configured **Priority > FIFO** within each category (oldest created first; number breaks ties). Unlabeled items follow configured priorities; highest tier wins if multiple labels exist.
4. MUST present **Open PR Candidates** and **Open Issue Candidates** separately, each with link/number, priority, age, and brief summary. SHOULD explain exclusions and surface possibly stale working claims separately, including owner/age evidence and the need for user approval before reclaiming. MAY show a bounded top list with total counts, never silently truncate the fetch.
5. MUST wait for user selection or an explicit Issue/PR number. MUST NOT mutate claims, auto-select the top candidate, start implementation, merge, or automatically continue after completion. If no candidates exist, explain why and STOP rather than inventing work.

## Route a selected item

An explicit user request for a specific Issue/PR counts as selection. MUST still re-fetch and claim through GitHub state before active work. A selected raw/ambiguous Issue routes to [triage-and-spec](../triage-and-spec/SKILL.md); a ready Issue to [implement-issue](../implement-issue/SKILL.md); an incomplete PR (including human-removed PASS) to [review-and-pass-pr](../review-and-pass-pr/SKILL.md). Selecting another session's stale claim requires explicit reclaim approval, not just generic next-task wording.

## Self-Check

- Claimed/blocked/passed, draft, linked, and dependency-unmet items excluded?
- PR and Issue categories separate; priority then FIFO exact?
- User selection awaited; no claim or work started implicitly?

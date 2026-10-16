---
name: workflow-status
description: Summarize actionable, claimed, blocked, dependency-waiting, and passed work without mutation.
layer: task
---

# Workflow status

Read [GitHub state](../github-state-and-labels/SKILL.md), [workflow model](../../reference/workflow-model.md), and [config](../../config.md). This task uses only the read/classify part of GitHub state, not its claim procedure.

## Procedure

1. MUST fetch complete live open PRs and Issues, labels, assignees, timestamps, linked Issues/PRs, dependencies, blocker/claim comments, and CI status. Follow pagination; unavailable access MUST be reported rather than shown as an empty queue.
2. MUST classify using model eligibility: exclude working/blocked/passed labels, draft PRs, linked Issue exclusions, unresolved prerequisites, and Issues already represented by any open linked PR. Sort actionable work by PR category before Issue category, configured priority, then FIFO.
3. MUST present these six clearly named sections, including explicit empty sections:
   - Actionable PRs: number/link, priority, age, summary, CI.
   - Actionable Issues: number/link, priority, age, summary.
   - Claimed / In-Progress Work (`agent:working`): owner/session and claim time when known; stale-looking claims are not automatically reclaimable.
   - Blocked Work (`agent:blocked`): blocker note and required human/external action; show unknown if undocumented.
   - Waiting on Dependencies: Issue/PR and unresolved prerequisite links/states.
   - Passed PRs Awaiting Human Merge (`agent:passed`): head and evidence references when available.
4. SHOULD identify excluded drafts and unlinked PRs separately if relevant. MAY note overlapping states and incomplete data without forcing items into a false single category.
5. MUST NOT mutate labels, files, comments, or Git state; MUST NOT claim work, reclaim a stale item, merge, or automatically select/start a task. End with the read-only report. A later user selection routes to the matching task.

## Self-Check

- All six status sections use complete live data or explicitly report access gaps?
- Priority/dependencies and linked PR suppression applied?
- No mutations or implicit task start?

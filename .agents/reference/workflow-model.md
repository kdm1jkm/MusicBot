# Workflow model

MUST, MUST NOT, SHOULD, and MAY express RFC 2119 contracts. Operational settings live in [config](../config.md). All tasks MUST link to a GitHub Issue; create one with user-authorized scope before implementation if none exists. One logical branch and PR per independently mergeable issue; base new work on `main` or the repository default branch, not another feature branch.

Language: write human-facing content (user replies, Issue/PR comments, snapshot section content, PR body, task-file content) in the language set by the config Language policy. Keep the headings and markers in this document verbatim in English; they are machine-located anchors. If an existing snapshot or task file has translated headings, read its content anyway and restore the English headings in the next update.

## Canonical Spec Snapshot

Preserve the original Issue body. Mature requirements in comments. Locate the latest trusted snapshot by comment chronology, then read all subsequent Issue and PR comments, including review threads; comments are feedback, not automatic permission to change scope. Do not treat quoted markers as a new authoritative spec. When the original body already provides an unambiguous, testable spec, a redundant snapshot is unnecessary.

Use this comment structure when consolidation is needed:

```markdown
## Canonical Spec Snapshot
Revision: <revision>; timestamp: <ISO-8601 UTC>; supersedes: <previous comment URL or none>

### Goal
<user outcome>

### Requirements
- <required behavior>

### Scope & Out of Scope
<included and excluded behavior>

### Constraints
<technical and policy constraints; documented assumptions>

### Acceptance Criteria
- [ ] <observable, checkable outcome>

### Open Questions
None

### Dependencies
- Depends on #<issue-number>
```

Replace `None` with unresolved questions when not ready. Dependencies may be `None`. Material scope/core behavior changes require user approval before a revised snapshot; never weaken requirements to fit partial implementation.

## Dependency DAG and splitting

Declare `- Depends on #<issue-number>` or `- Blocked by #<issue-number>` under `### Dependencies` in the original Issue or snapshot. Resolve both sources; a newer snapshot MUST NOT silently drop original prerequisites. Removing a prerequisite requires an explicit approved change. Referenced issues MUST all be `CLOSED` before work is actionable. Unknown/inaccessible state is not satisfied. Resolve references in the correct repository; fetch each prerequisite and detect cycles, self-dependencies, and missing issues. Report these as non-actionable rather than guessing.

Oversized tasks MUST be split only after the user approves boundaries. Create concrete child Issues, link parent/children in comments, and declare an acyclic dependency graph. Each child MUST be independently mergeable into the default branch; do not use stacked PRs by default. A parent coordinating unfinished children is not itself an implementation candidate.

## Priority queue

Fetch all pages of open Issues, PRs, comments, review threads, and dependency data; API truncation is not a complete queue. GitHub's Issues REST endpoint also returns PRs: distinguish them. Discover linked PRs from closing references and timeline/linked relationships, not title matching alone.

Exclude `agent:working`, `agent:blocked`, and `agent:passed` items, unmet prerequisites, and issues already represented by an open linked PR (including draft or passed PRs). PR eligibility also depends on the linked Issue's labels and prerequisites; a PR without a linked Issue must be repaired through triage before becoming actionable. Draft PRs are excluded, not silently converted to ready.

Sort by:

1. Incomplete, non-draft Open PRs.
2. Actionable Open Issues without any open linked PR.
3. Within each category, configured priority tier descending; highest label wins if several exist, unlabeled last.
4. FIFO by `created_at` ascending, then number ascending for ties.

Present **Open PR Candidates** and **Open Issue Candidates** separately with number/link, priority, age, and summary. Never auto-select; wait for user choice. An explicit request for a particular Issue/PR is a selection, not permission to bypass claims/dependencies. Surface possibly stale claims separately; age alone never authorizes reclaiming.

## State and claim lifecycle

| Event | Issue | PR |
| --- | --- | --- |
| Claim selected work after live recheck | add `agent:working` | add `agent:working` if present |
| User-requested handoff | remove own `agent:working` | remove own `agent:working`; leave unpassed |
| External/human blocker | remove own working; add blocked where relevant | same; comment reason/action |
| PASS | remove own working | remove own working; add `agent:passed` |
| Human requests post-PASS rework | no automatic mutation | human removes `agent:passed`; PR becomes eligible if otherwise actionable |
| Human merge | closing reference closes Issue | merged by human using configured strategy |

Labels are not an atomic lock. Re-fetch immediately before claiming, publish session/owner, start time, and intended work in a claim comment, apply labels, then re-fetch for competing claims. If ownership is uncertain, stop and coordinate; never assume exclusivity. On partial claim failure release only labels owned by this session and report. Ask the user before reclaiming another session's stale claim. Never remove PASS just because feedback arrived. Clear a blocked label only after its documented blocker is resolved and authorized work is rechecked.

## PASS gate

PASS requires BOTH objective evidence and independent agent quality judgment:

- All current Acceptance Criteria satisfied; behavior tests and relevant smoke scenarios exercised.
- Applicable local test, lint, typecheck, and build commands pass; unavailable required checks remain unresolved.
- Required CI checks pass for the exact final pushed head; pending, skipped-required, cancelled, or missing checks are not proof. Identify required checks from repository rules and workflows, not only visible green jobs.
- An independent agent/reviewer reviews current spec and final diff for correctness, maintainability, unrelated noise, security, and regressions. Record reviewer identity/context, reviewed commit, findings and resolutions. An implementer's self-review alone is insufficient. If no independent agent is available, hand off without PASS.
- Final retention cleanup is committed/pushed before final checks/review; any substantive later change invalidates affected evidence. Never label an older head based on newer local results or vice versa.

Self-review remains required before independent review. Evidence-driven self-repair follows config without a fixed iteration limit; preserve diagnoses and verification across skills and phases and continue actionable repairs. Do not repeat an unchanged failure without new evidence. Stop for a user-requested handoff or genuine human/external prerequisite, not an accumulated repair count. PASS means awaiting human merge, not automatic merge. Completion, authorized handoff, and blocking always end with a report and STOP, never starting the next item.

## Task file and handoff

One free-form Markdown file per Issue: `.agents/tasks/<issue-number>.md` (repository-relative). No mandatory YAML schema. Recommended headings:

```markdown
# Issue #<N>: <Title>
## Reference
Issue URL, PR URL / branch, latest spec comment URL
## Current Status
Session/claim owner and current head
## Implementation Plan (Disposable)
## Completed Work
## Verification Results
Commands, outcomes, head, CI links, independent review
## Key Decisions
## Unresolved Issues & Handoff
Blocker/action, repair attempts used, exact resume instructions
```

Resume truth order: original user intent → latest spec plus subsequent comments → actual code/Git → objective checks/CI → task file. Plans are disposable; constraints and decisions must remain traceable. Apply configured retention; mirror essential handoff to GitHub for local-only files. Domain/architecture decisions belong in project documentation, never agent task state in `docs/`.

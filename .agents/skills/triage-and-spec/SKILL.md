---
name: triage-and-spec
description: Analyze a selected Issue, clarify material ambiguity, split scope, and publish canonical specs.
layer: task
---

# Triage and spec

Read [spec management](../spec-management/SKILL.md), [GitHub state](../github-state-and-labels/SKILL.md), [handoff](../task-file-and-handoff/SKILL.md), and [workflow model](../../reference/workflow-model.md).

## Procedure

1. MUST have a selected GitHub Issue. If the user supplied only an idea, establish authorized Issue scope and create/link an Issue before active task work. MUST verify live eligibility/dependencies and claim `agent:working` before authoring triage/spec comments. A continuing session MAY keep its own verified claim.
2. MUST read original body, all relevant discussion, latest `## Canonical Spec Snapshot` plus subsequent Issue/PR comments, related code and constraints, and any task file using the truth hierarchy. Preserve the original body.
3. MUST assess PR size. If oversized, propose concrete independently mergeable child Issues with `Depends on #N` DAG, all targeting main/default branch; obtain user boundary approval before creating children. Publish parent/child references in comments and do not continue implementing the oversized parent.
4. MUST assess clarity. If requirements and testable Acceptance Criteria are already concrete, SHOULD skip redundant snapshot rewriting. If clarification is needed, document minor reasonable assumptions; ask concise high-impact questions only for material scope/core behavior/user-visible uncertainty. Await required approval; unresolved material questions mean not ready.
5. MUST publish a consolidated snapshot comment when needed using every required section and explicit Open Questions, revision/time and dependency declarations. Controlled evolution cannot delete original requirements or weaken criteria to fit code. Link the current authoritative body/comment in the task file.
6. MUST update `.agents/tasks/<issue-number>.md` under configured retention and release the working claim on handoff. If implementation was already explicitly authorized and the same Issue is ready, MAY immediately continue to [implement-issue](../implement-issue/SKILL.md) retaining its own claim and repair history; an analyze-only request does not authorize implementation. Otherwise report readiness, questions, split links or blocker, and STOP.

## Self-Check

- Selected Issue claimed safely, original body intact and all newer feedback considered?
- Split/material changes approved; snapshot only when useful; checkable criteria ready?
- Task state updated and claim released unless continuing already-authorized same-Issue work?

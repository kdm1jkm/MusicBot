---
name: tdd-and-verification
description: Apply TDD, verify behavior and CI, bound repairs, and require independent PASS review.
layer: capability
---

# TDD and verification

Read [config](../../config.md), [PASS gate](../../reference/workflow-model.md), and [handoff](../task-file-and-handoff/SKILL.md).

## Red → Green → Refactor

1. MUST map current spec Acceptance Criteria to observable behavioral checks. Use existing tests before adding redundant tests. MUST create executable tests before or alongside implementation; bug fixes SHOULD begin with a failing reproduction test.
2. MUST observe Red for the intended reason when practical, implement the smallest correct change, observe Green, and refactor while preserving behavior. MUST NOT weaken requirements or delete valid tests to pass. Tests should catch consumer-visible bugs, boundaries, state transitions, and errors, not implementation wording or mock echoes.
3. MUST use repository test/lint/typecheck/build commands recorded in config and existing CI conventions; MUST NOT add new frameworks without demonstrated need. For documentation-only or other non-executable criteria, record a concrete walkthrough/check and explain why an application test is inapplicable, rather than inventing a fake test.
4. MUST exercise the changed runtime path with a smoke scenario where one exists; tests alone do not establish the actual UI/CLI behavior. Record commands, outcomes, relevant environment and commit; never claim an unexecuted check passed.

## Bounded self-repair

MUST read `K` (default 3) and track repairs across the session, including local, CI, and review failures. Initial verification is not a repair. On failure MAY perform up to K repair iterations, each with diagnosis, related fix, and rerun of affected checks. MUST NOT reset K by changing skills or starting review. At K unresolved attempts, stop repairing: record root cause, evidence and attempted fixes in the task file, sync reachable Git/PR state, release working labels, hand off, and STOP. Use blocked only for a real human/external dependency, not to hide an ordinary unresolved failure.

## Dual PASS gate

- MUST satisfy every Acceptance Criterion and all applicable tests, lint, typecheck, build and required CI checks. Missing tools/credentials, unknown applicability, pending CI, and failures are unresolved, not green. Record reason for genuinely inapplicable checks.
- MUST verify CI for the final pushed head against repository required checks. MAY wait for in-flight checks or hand off; MUST NOT label passed prematurely.
- MUST self-review the whole diff against the current spec, then obtain independent agent quality review of correctness, readability, maintainability, unrelated noise, security, and regressions. A separate agent/reviewer session MUST receive the spec, final diff/head, and verification evidence; implementer's self-review is not independent. Record reviewer identity/context, head, findings, and resolutions. If unavailable, hand off without PASS.
- MUST apply retention cleanup before final verification and review. Any subsequent substantive edit requires refreshed relevant checks and independent review. Both objective checks AND independent judgment MUST pass for the same final head.

## Self-Check

- Acceptance Criteria have behavioral evidence; Red/Green or justified non-runtime walkthrough recorded?
- Applicable commands and required CI pass at the final head, not merely a previous commit?
- Independent review distinct from self-review; repair budget respected; no premature PASS?

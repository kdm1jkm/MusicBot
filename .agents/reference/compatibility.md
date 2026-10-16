# Agent compatibility

| Agent | Root context | Repository skills | Execution |
| --- | --- | --- | --- |
| Codex | Native `AGENTS.md` | `.agents/skills/<name>/SKILL.md` | Standard Git/GitHub CLI or available native tools |
| Claude Code | `CLAUDE.md` imports `@AGENTS.md` | Follow relative links in the index; native slash-command discovery may require an approved `.claude/skills` bridge | Same contracts, no adapter |
| OMP / oh-my-pi | Native `AGENTS.md` | `.agents/skills/<name>/SKILL.md` through enabled agents/codex skill providers | Same contracts, no adapter |

These are file-layout contracts, not a claim that every installed agent version/provider has been executed. During target initialization verify actual discovery with the installed agent, enabled providers, and repository trust settings. If native skill discovery is unavailable, explicitly read the linked `SKILL.md`; do not silently omit its contract.

## Layout rules

- Keep all eleven skills one directory deep under `.agents/skills/`; distinguish task/capability using `layer` frontmatter and the root index, never nested group directories.
- Each skill has YAML frontmatter containing `name`, `description`, and `layer`, and ends with Self-Check.
- Root-relative paths in inline code are descriptions; Markdown links resolve relative to their containing document. Read linked capability skills before running a task procedure.
- Keep `AGENTS.md` a minimal routing index. Preserve existing project instructions during selective merge. `CLAUDE.md` is a one-line import only in new repositories; append/deduplicate that import in existing ones without replacing their content.
- An optional `.claude/skills` symlink (platform permissions permitting) or copy requires user approval when needed for native discovery. Prefer linked reading without a second skill source. Copies must be generated from the installed canonical files and checked for divergence; no ongoing template synchronization is required.

## Tools and permissions

Use ordinary file tools, `git`, and authenticated `gh` or equivalent agent-native GitHub tools. No orchestrator, daemon, custom binary, global agent configuration, or agent-specific runtime adapter is required. Native tools must provide the same complete data and mutations, including pagination, review threads, check conclusions and linked issues. Unavailable access must be reported, never interpreted as empty data or success.

Independent review can use a separate agent tool or a separate reviewer session with only the current spec/diff/check evidence. If the host cannot provide independent review, preserve handoff and leave PR unpassed. Do not add an MCP just to simulate a reviewer.

## Initialization compatibility smoke checklist

1. From each available agent, load root context; confirm the same task routes are visible.
2. Read a Task Skill and follow its relative links to the Capability Skills and workflow model.
3. Confirm all eleven flat skills are discoverable or explicitly readable without global installation.
4. Exercise status/selection without mutation; confirm user-selection pause.
5. Inspect Git/GitHub tool access without exposing tokens; document missing agents/tools as not exercised.

See [workflow model](workflow-model.md) and [configuration](../config.md).

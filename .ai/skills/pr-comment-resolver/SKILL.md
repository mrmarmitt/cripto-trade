---
name: pr-comment-resolver
description: Resolve actionable GitHub pull request review comments for the ctrade repository end to end. Use when Codex is asked to address PR comments, requested changes, unresolved review threads, reviewer feedback, or to implement fixes and reply back on a PR. This skill covers inspecting thread-aware review context, deciding which comments require code or explanation, implementing fixes, validating them, drafting replies, and publishing replies/resolving threads only when explicitly requested.
---

# PR Comment Resolver

Address review comments as a complete workflow: understand the thread, make the
code change when needed, validate it, and leave a traceable reply plan.

This skill is project-specific. It does not replace the generic GitHub
`gh-address-comments` workflow; use that GitHub skill/tooling when available for
thread-aware PR reads.

## Required Reading

Before editing code, read:

- `/.ai/README.md`
- `/.ai/project-context.md`
- `/.ai/architecture.md`
- `/.ai/coding-standards.md`
- `/.ai/validation.md`

For comments that imply review policy or architectural boundaries, also read:

- `/.ai/review.md`
- `/.ai/agents/code-review-agent.md`
- `/.ai/change-safety.md`

For publishing replies or review comments, also read:

- `/.ai/skills/pr-review-publisher/SKILL.md`

## Workflow

1. Resolve the target PR.
   - Use the PR URL/number if provided.
   - If the user refers to the current branch, infer the PR from local git and
     GitHub metadata.
2. Fetch thread-aware review context.
   - Prefer the GitHub plugin skill `gh-address-comments` when available.
   - Use `gh` GraphQL/thread-aware reads when unresolved state, inline anchors,
     outdated state, or review thread resolution matters.
   - Do not rely only on flat PR comment lists for actionable review work.
3. Classify every comment.
   - Actionable code change.
   - Explanation-only reply.
   - Ambiguous or conflicting feedback.
   - Duplicate, outdated, resolved, or informational feedback.
4. Confirm scope when needed.
   - If the user asks to fix everything, address all unresolved actionable
     comments and call out ambiguous ones.
   - If comments conflict or could cause regression, stop and explain the
     tradeoff before editing.
5. Implement selected fixes.
   - Keep each change traceable to a comment or feedback cluster.
   - Preserve module ownership from `/.ai/architecture.md`.
   - Do not move provider payloads, framework details, or business rules across
     boundaries for convenience.
6. Validate with the narrowest meaningful command.
   - Use `./scripts/gradle-run.ps1` commands from `/.ai/validation.md`.
   - If behavior is financial, stateful, idempotent, or cross-module, prefer a
     targeted test in addition to compile when feasible.
7. Prepare replies.
   - For each addressed thread, draft a concise reply covering what changed and
     what validation ran.
   - For explanation-only or intentionally skipped threads, draft the rationale.
   - Use `references/response-template.md` when writing replies.
8. Publish only with explicit permission.
   - Do not post GitHub replies, resolve review threads, submit reviews, merge,
     or push unless the user explicitly asks for that action.
   - If the user says "responda no PR", "publique as respostas", "resolva os
     threads", or equivalent, use the GitHub connector or `gh` as appropriate.

## Definition of Done

A PR comment resolution task is not complete until the final report includes:

- comments addressed, grouped by thread or behavior area;
- files/behavior changed;
- validation commands run and their result;
- reply drafts or confirmation that replies were posted;
- comments left unresolved and why.

## Reply Rules

- Keep replies short and specific.
- Tie the reply to the reviewer concern, not to internal reasoning.
- Mention validation only when it actually ran.
- If no code changed, say whether the comment was outdated, non-actionable, or
  intentionally handled by explanation.
- If a requested change was rejected, explain the architectural or behavioral
  reason and identify the safer alternative.

## Reference Map

- `references/response-template.md`: compact templates for addressed,
  explanation-only, skipped, and blocked review threads.


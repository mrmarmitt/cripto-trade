---
name: "pr-review-publisher"
description: "Publish finalized code review findings back to a GitHub pull request in the ctrade repository. Use when Codex needs to turn review output into PR comments, inline review comments, summary comments, or replies on existing review threads. Use only after findings are stable or when the user explicitly asks to post, publish, submit, or mirror the review in the PR."
---

# PR Review Publisher

Operationalize publication of review findings to GitHub pull requests for this repository.

This skill does not decide whether a finding is valid. The review policy stays in `/.ai/agents/code-review-agent.md`, backed by `/.ai/review.md`, `/.ai/architecture.md`, `/.ai/coding-standards.md`, and `/.ai/validation.md`.

## Core rules

- Publish comments only when the user explicitly asks to post, publish, submit, mirror, or reply in the PR.
- If the user only asked for a review, stop at findings and do not publish anything.
- Prefer one concrete concern per comment.
- Prefer inline comments only when the concern can be anchored to a changed line in the PR.
- Use top-level PR comments for cross-file, architectural, or non-anchorable concerns.
- Do not publish style-only comments unless they affect maintenance, intent, or safety.
- Do not duplicate an existing unresolved comment that already captures the same concern.
- Default to a non-blocking review comment. Use a blocking review state only if the user explicitly asks for it.

## Required inputs

Gather or infer these inputs before publishing:

- repository name in `owner/name` form
- pull request number
- finalized findings or the exact comment text to publish
- whether the user wants inline comments, a summary comment, a formal review, or replies to an existing thread

If the repository or PR number cannot be inferred safely, ask for the missing value.

## Workflow

1. Read `/.ai/README.md`, `/.ai/review.md`, and `/.ai/agents/code-review-agent.md`.
2. Fetch the PR metadata and changed files before writing comments.
3. Fetch existing PR comments and review threads to avoid duplication.
4. Classify each finding:
   - inline review comment for a file-specific issue anchored to a changed line
   - top-level PR comment for cross-file or unanchorable issues
   - reply for continuing an existing discussion thread
5. Publish using the GitHub connector when available.
6. If the GitHub connector is unavailable, fall back to `gh` CLI commands described in `references/tooling.md`.
7. Report back what was posted, what was skipped, and why.

## Preferred tools

Use these GitHub operations when available:

- fetch PR context: `_fetch_pr`, `_fetch_pr_comments`, `_list_pr_changed_filenames`, `_fetch_pr_file_patch`
- publish summary comment: `_add_comment_to_issue`
- publish inline or grouped review comments: `_add_review_to_pr` with `COMMENT`
- reply in existing thread: `_reply_to_review_comment`

Use `_add_review_to_pr` with `REQUEST_CHANGES` only when the user explicitly asks for a blocking review.

## Comment mapping rules

- Inline comment:
  - use when a single finding maps to a specific changed file and diff line
  - keep the comment focused on one issue
- Top-level comment:
  - use for architectural findings spanning multiple files
  - use when the PR diff does not expose a safe anchor line
- Reply:
  - use when a thread already exists for the same concern and the user wants to continue that thread instead of posting a duplicate

## Writing rules

Each published comment should:

- identify the concrete risk or regression
- name the violated boundary, contract, or invariant when applicable
- explain the behavioral or maintenance impact in direct language
- point to the owning module or responsibility when that matters

Keep comments concise and review-oriented. Avoid hidden reasoning, broad speculation, or style nitpicks.

## Output contract

After publishing, report:

- repository and PR targeted
- comment mode used: inline, top-level, review, or reply
- which findings were posted
- which findings were skipped as duplicates or non-anchorable
- URLs or IDs returned by the tooling when available

## Reference map

- `references/publication-workflow.md`: decision flow, dedupe policy, and publishing sequence
- `references/tooling.md`: GitHub connector and `gh` CLI publication recipes

# Tooling

## Preferred path: GitHub connector

Use the GitHub connector when available because it provides structured access to pull request metadata and comment publishing.

Recommended sequence:

1. `_fetch_pr`
2. `_fetch_pr_comments`
3. `_list_pr_changed_filenames`
4. `_fetch_pr_file_patch` for inline anchoring
5. publish with one of:
   - `_add_comment_to_issue`
   - `_add_review_to_pr`
   - `_reply_to_review_comment`

Default review submission mode:

- use `COMMENT`

Use `REQUEST_CHANGES` only with explicit user intent.

## Fallback path: gh CLI

If the connector is unavailable, use GitHub CLI.

Typical operations:

```bash
gh pr view <number> --repo <owner/name> --comments
gh api repos/<owner>/<repo>/pulls/<number>/comments
gh pr review <number> --repo <owner/name> --comment --body "<summary>"
```

For inline comments, prefer the structured connector. If only `gh` is available and inline publication is too fragile for the current diff, fall back to a top-level PR comment and explain that the point could not be anchored safely.

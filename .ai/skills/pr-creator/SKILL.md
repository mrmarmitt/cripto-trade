---
name: pr-creator
description: Create a GitHub pull request for scoped local changes in the ctrade repository. Use when Codex is asked to validate changes, prepare a commit, push a branch, open a PR or draft PR, publish current work for review, or turn local implementation changes into a pull request. This skill covers checking git status, separating related from unrelated changes, running project validation, creating a scoped commit, pushing the branch, opening a draft PR by default, never merging, and reporting the PR URL, validation, and included files.
---

# PR Creator

Publish local work as a reviewable pull request without losing scope control.

This skill is for creating/updating a PR from local changes. It is not for code
review findings (`pr-review-publisher`) or for addressing reviewer comments
(`pr-comment-resolver`).

## Required Reading

Before committing or pushing, read:

- `/.ai/README.md`
- `/.ai/project-context.md`
- `/.ai/architecture.md`
- `/.ai/coding-standards.md`
- `/.ai/validation.md`
- `/.ai/git-workflow.md`

For refactors or responsibility moves, also read `/.ai/change-safety.md`.

## Safety Rules

- Commit, push, and PR creation require an explicit user request such as
  "faca commit", "suba a branch", "abra PR", or "crie um PR".
- Open PRs as draft by default unless the user explicitly asks for ready review.
- Always open PRs targeting `develop`.
- Never merge a PR.
- Never run `git add .` or equivalent broad staging. Stage explicit related
  paths only.
- Never include unrelated dirty work. If unrelated changes exist, leave them
  unstaged and report them.
- Do not rewrite, amend, squash, or force-push unless explicitly asked. The
  required rebase onto `develop` is the only default rebase allowed by this skill.
- Do not publish secrets, local credentials, generated caches, or build outputs.

## Workflow

1. Inspect repository state.
   - Run `git status --short`.
   - Identify current branch and upstream state.
   - If on `main`, `master`, or `develop`, create a topic branch before commit.
     Use the branch naming policy from `/.ai/git-workflow.md`.
   - Before creating a new topic branch, update `develop` and branch from it.
   - If the user supplied a branch name that does not follow
     `/.ai/git-workflow.md`, suggest the corrected name before creating it.
2. Classify changes.
   - Group modified/untracked files as related, unrelated, generated, or unsafe.
   - If scope is ambiguous, ask before staging.
   - Use `git diff` and targeted file reads to understand related changes before
     writing the commit message or PR body.
3. Validate.
   - Use the narrowest meaningful `./scripts/gradle-run.ps1` command from
     `/.ai/validation.md`.
   - For docs-only changes, no Gradle command is required; report that validation
     was documentation-only inspection.
   - If validation fails, stop before commit unless the user explicitly asks to
     publish a failing/WIP PR.
4. Prepare commit.
   - Stage only related paths.
   - Use a concise conventional commit style when it fits the change.
   - Commit message must describe the behavior or workflow changed, not the tool.
5. Push branch.
   - Rebase the current branch on `develop` before pushing/opening the PR.
   - If rebase conflicts occur, stop and report the conflict.
   - Push the current topic branch to `origin`.
   - Set upstream when needed.
6. Open PR.
   - Prefer GitHub connector tools when available; otherwise use `gh`.
   - Use `develop` as the PR base branch.
   - If `develop` cannot be found locally or remotely, stop and report the
     blocker instead of choosing another base branch.
   - Do not open the PR until the branch is rebased onto `develop`.
   - Create as draft unless the user explicitly asks for ready review.
   - Use `references/pr-body-template.md` for title/body structure.
7. Report result.
   - Include PR URL.
   - Include branch, base, commit hash if available.
   - Include files staged/committed.
   - Include validation command and result.
   - Include unrelated changes left out.

## PR Body Requirements

The PR body should include:

- Summary: 2-4 bullets describing what changed.
- Validation: exact commands run and outcome, or docs-only inspection.
- Scope notes: related files included and unrelated files intentionally left out
  when relevant.
- Risk notes: only if there is residual risk, skipped validation, or a known
  follow-up.

## Failure Handling

- Dirty unrelated files: continue only with explicit scoped staging; report what
  was excluded.
- Invalid branch name: stop before creating the branch and propose a compliant
  Git Flow name.
- Rebase conflict with `develop`: stop before push/PR and report the conflicting
  files.
- Missing GitHub auth or network failure: stop after commit only if the commit was
  already requested and created; otherwise stop before writes.
- Validation failure: stop before commit/PR by default and report the failing
  command.
- Existing PR for branch: update/report the existing PR instead of creating a
  duplicate.
- Missing `develop` base branch: stop before PR creation and report the blocker.

## Output Contract

After running this skill, report:

- `PR`: URL or "not created" with reason.
- `Branch`: local and remote branch.
- `Commit`: commit hash/message, if created.
- `Validation`: command and result.
- `Included`: files committed.
- `Excluded`: unrelated or unsafe files not included.

## Reference Map

- `references/pr-body-template.md`: compact PR title/body template.

# Publication Workflow

## Sequence

1. Confirm the user explicitly asked to publish or reply in the PR.
2. Load the finalized findings from the review.
3. Fetch PR metadata, changed files, existing comments, and review threads.
4. Decide the publication shape for each finding:
   - inline comment
   - top-level comment
   - reply to existing thread
5. Skip duplicates already covered by an unresolved comment unless the user asked to restate them.
6. Publish the comments.
7. Return a compact posting report with IDs or URLs when available.

## Dedupe policy

Treat a comment as duplicate when all of these are true:

- it points to the same behavior or architectural concern
- it targets the same file or same review thread
- it is still unresolved or clearly active

If the previous thread exists but needs continuation, reply in that thread instead of posting a new standalone comment.

## Anchoring policy

Use inline comments only when the finding can be anchored safely to a changed line in the PR diff.

Fall back to a top-level comment when:

- the relevant code is not part of the visible diff
- the concern spans more than one file
- the issue is architectural and no single line explains it well

## Comment shape

Recommended comment structure:

1. concrete issue
2. why it is risky
3. expected owner or boundary, when relevant

Example:

`This moves strategy decision logic into spring-application. That makes the delivery layer own domain policy and increases the chance of divergence from the strategy module. The calculation should stay in strategy and be consumed here as wiring only.`

# PR Body Template

Use this as the default PR shape. Keep it concise and factual.

## Title

Use a short imperative or conventional title:

```text
docs(ai): add PR creator workflow
```

or:

```text
Add Binance symbol filter validation
```

## Body

```md
## Summary
- <change 1>
- <change 2>
- <change 3 if needed>

## Validation
- `<command>`: passed

## Documentation
- <updated docs, or no documentation impact>

## Notes
- <optional: unrelated files left out, residual risk, skipped validation, or follow-up>
```

## Docs-only Validation

```md
## Validation
- Docs-only change; inspected the updated Markdown files.

## Documentation
- Updated base documentation.
```

## Failed or Skipped Validation

```md
## Validation
- `<command>`: failed with <short reason>

## Notes
- PR opened as draft/WIP because <explicit user request or reason>.
```


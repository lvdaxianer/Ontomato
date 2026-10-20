# Tasks

## 1. Standalone workbench localization

- [x] 1.1 Add checked-in Simplified Chinese resources for the browser and server workbench packs, configure the standalone workbench as Chinese-first with English selectable, propagate the active locale to data-engine requests, and update focused tests for defaults, switching, resource coverage, and request headers; verify with the focused Vitest tests, workbench lint, typecheck, and workbench test suite.

## Workflow follow-up

- Run OpenSpec validation, plan-implementation consistency review, code review,
  per-task audit, and secret-leak checks before committing.
- Merge the completed task branch into `features/doris`, then archive the
  completed OpenSpec change after final verification.

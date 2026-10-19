# Tasks

## 1. Controller response normalization

- [x] 1.1 Add a focused `MetricViewController` regression test first, verify it fails against the current one-element array response, then change only `POST /metricView/execute` to unwrap exactly one result object while preserving empty and multi-item arrays; verify with the focused Maven test and `git diff --check`.

## Workflow follow-up

- Run the per-task audit, including scope and backend API contract review, before marking the task complete.
- Validate and archive the completed OpenSpec change.
- Commit the task on the fix branch, merge it into `features/doris`, verify the integration checkout, then remove the task worktree and branch.

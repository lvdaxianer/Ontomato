# Tasks

## 1. Unify metric view execution response normalization

- [x] 1.1 Add failing coverage for `testExecute` single/multiple/empty result shapes, implement the shared controller normalization for both endpoints, preserve completeness metadata, and pass the focused and broader checks.

### Task boundary / dispatch plan

- Module-oriented owner: data-engine controller contract.
- Allowed files: `packages/data-engine-core/src/main/java/io/ontomato/dataengine/controller/MetricViewController.java`, `packages/data-engine-core/src/test/java/io/ontomato/dataengine/controller/MetricViewControllerExecuteTest.java`, `apis/metric-view.md`, this OpenSpec change, and the brainstorming design note.
- Out of scope: service implementations, frontend code, database/configuration, and unrelated endpoints.
- Dependency: existing `MetricViewService` result shape.
- Focused verification: `mvn -pl packages/data-engine-core -Dtest=MetricViewControllerExecuteTest test`.
- Broader verification: the same module test plus `git diff --check`.
- Direct main-agent execution is selected because the controller and its single focused test are a shared, two-file scope where delegation would add coordination risk without useful parallelism.

## Workflow follow-up

- Run the plan-implementation audit, backend reliability review, code-review-spec review, and per-task audit before marking the task complete.
- Validate and archive the completed OpenSpec change.
- Commit the task on the fix branch, merge it into `features/doris`, verify the integration checkout, then remove the task worktree and branch.

# Tasks

## 1. Relation Metadata Contract

- [x] 1.1 Add a failing API regression test proving that persisted `fromField` and `toField` values are returned in the normalized relation model; verify the test fails against the current parser.
- [x] 1.2 Extend the relation metadata type and normalization to preserve optional join fields, then run the focused ontology-manager test suite to verify the RED test turns GREEN and legacy relations still load.
- [x] 1.3 Update visual-modeling metadata loading to retain join fields for existing relations and verify the Manager typecheck plus component tests.

## 2. Manager Artifact and Deployment

- [x] 2.1 Build the amd64 standalone Ontology Manager image from this public checkout and verify image identity, compiled assets, and the focused test suite.
- [ ] 2.2 Deploy only the Ontology Manager image to 10.100.31.37, preserving the existing data engine and Nginx timeout configuration; verify the service, route, and relation-field bundle behavior.

## Workflow follow-up

- Archive the completed OpenSpec change after review and verification.
- Merge the task branch into the recorded `features/doris` integration branch and remove the task worktree only after the merge succeeds.

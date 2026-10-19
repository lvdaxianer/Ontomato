# Tasks

## 1. Locale resources and contracts

- [x] 1.1 Add failing resource coverage tests proving `zh-CN` provides all current Manager and shared message keys and preserves interpolation placeholders.
- [x] 1.2 Add the complete Simplified Chinese Manager resource and Element Plus locale, then verify the resource coverage and i18n tests pass.

## 2. Standalone shell behavior

- [x] 2.1 Add failing shell tests for the Chinese default and explicit English selection.
- [x] 2.2 Register both locales in the standalone shell, default to `zh-CN`, and verify shell type checks and tests pass.

## 3. Artifact and deployment verification

- [x] 3.1 Build the production Manager bundle and amd64 image, then verify Chinese text and locale assets are present.
- [x] 3.2 Deploy only the Manager image to 10.100.31.37, preserve the existing Nginx timeout and rollback image, and verify the Chinese page and service health.

## Workflow follow-up

- Archive the completed OpenSpec change after final review and verification.
- Merge the task branch into `features/doris` and remove the task worktree after the merge succeeds.

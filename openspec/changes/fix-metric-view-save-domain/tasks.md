# Tasks

## 1. Regression Test and Service Fix

- [x] 1.1 Add a service-level regression test for saving a metric view whose nested function has no domain identifier, and verify it fails before the fix with the missing propagation assertion.
- [x] 1.2 Normalize the nested function to the authenticated metric-view domain and reject a missing domain before DAO calls; verify the focused regression test passes.
- [x] 1.3 Reject an existing function identifier from another domain before persistence, and cover the tenant-isolation guard with a regression test.

## 2. Verification and Release

- [x] 2.1 Run the focused data-engine tests and the applicable broader module checks, and verify no unrelated test regression is introduced.
- [ ] 2.2 Review the diff and OpenSpec coverage, commit the fix with required traceability, and push the integration branch.
- [ ] 2.3 Back up the active enterprise DataRAG deployment, build and deploy the corrected image, and verify health, Doris data browsing, and metric-view save behavior on `10.100.31.37`.

## Workflow follow-up

- Archive the completed OpenSpec change after the final audit.
- Verify the archived change and the final integration branch.

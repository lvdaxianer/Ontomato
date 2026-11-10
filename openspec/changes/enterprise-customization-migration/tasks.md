# Tasks

## 1. Establish the matched source baseline

- [x] 1.1 Resolve and record the enterprise `release-v4.1.1` public `PUBLIC_SHA`, verify both repository worktrees are clean, and produce the customization-to-module migration matrix; verify the recorded source pair matches the enterprise workspace paths and no production files are changed.

  - Boundary: source pairing, commit inventory, and migration evidence only.
  - Owner: `baseline-coordinator`.
  - Allowed files: public/enterprise Git metadata, OpenSpec evidence, and migration matrix documentation.
  - Out of scope: production code changes, server deployment, database writes.
  - Dependencies: paired task worktrees and current integration branches.
  - Focused verification: `git worktree list --porcelain`, `git status --short`, public SHA check, and source-path/module existence checks.
  - Broader verification: `openspec validate enterprise-customization-migration --strict` after all planning artifacts are present.
  - Handoff evidence: exact public SHA, enterprise commit, changed-commit groups, target repository for each customization, and unresolved conflicts.

## 2. Port public customization behavior

- [x] 2.1 Port Doris adapter behavior and its tests into the matched public/Data Engine source used by the enterprise build; verify adapter identity, read-only FE verification, PostgreSQL product runtime compatibility, and no M3 runtime dependency.

  - Boundary: Doris adapter, adapter contracts, Data Engine module lists, and focused Java tests.
  - Owner: `data-engine-doris-migrator`.
  - Allowed files: public `packages/data-adapters/doris`, public Data Engine contracts/build files, enterprise Data Engine source/build declarations only where required to include Doris.
  - Out of scope: M3 service, Oracle/DB2/DM behavior, enterprise authentication, production deployment.
  - Dependencies: task 1 source pair.
  - Focused verification: module-scoped Doris JDBC/adapter tests and the read-only real-FE test when credentials are available.
  - Broader verification: enterprise Data Engine compile/package with `LICENSE_MODE=real` and PostgreSQL/Doris adapter discovery.
  - Handoff evidence: diff, focused test output, adapter list, and confirmation that M3 is not a health prerequisite.

- [x] 2.2 Port MetricView response and Python formatting behavior into the matched public contracts/server/UI source; verify the single-result, multi-result, empty-result, completeness metadata, and formatting contracts.

  - Boundary: MetricView contracts/routes/consumers and Python-format endpoint behavior.
  - Owner: `workbench-contracts-migrator`.
  - Allowed files: public `packages/contracts`, `packages/workbench-server`, `packages/workbench-ui`, relevant API docs/tests.
  - Out of scope: enterprise auth and permission policy, unrelated API redesign, deployment configuration.
  - Dependencies: task 1 source pair.
  - Focused verification: existing MetricView and Python-format tests for the touched modules.
  - Broader verification: public/enterprise typecheck and API documentation coverage for affected public routes.
  - Handoff evidence: behavior tests, route-to-module mapping, and compatibility notes for enterprise consumers.

- [x] 2.3 Port ontology version management, relation join fields, code editor, editor cache, and localization behavior into the matched public Manager/UI source; verify Doris SQL relation fields, legacy metadata loading, Chinese/English behavior, and editor workflows.

  - Boundary: public Ontology Manager package, public Manager app shell inputs, and related Workbench UI consumers.
  - Owner: `ontology-manager-migrator`.
  - Allowed files: public `packages/ontology-manager`, public `apps/ontology-manager`, public `packages/workbench-ui`, and focused tests/spec evidence.
  - Out of scope: replacing the enterprise Manager host shell, enterprise authentication, enterprise admin routes.
  - Dependencies: task 1 source pair; task 2.1 Doris contract where relation behavior depends on adapter capability.
  - Focused verification: Manager relation/version/editor/localization tests and targeted UI component tests.
  - Broader verification: enterprise Manager build and Workbench web typecheck/build.
  - Handoff evidence: feature-by-feature screenshots or test output, legacy compatibility result, and list of enterprise shell conflicts resolved.

## 3. Integrate the enterprise overlay

- [x] 3.1 Adapt enterprise Workbench, Manager, Data Engine, and deployment assembly to consume the migrated public behavior while preserving enterprise identity, permissions, license enforcement, PostgreSQL configuration, and Doris support; verify no public anonymous assembly replaces enterprise boundaries.

  - Boundary: enterprise `apps/*`, `packages/*-enterprise`, `deploy`, and build scripts.
  - Owner: `enterprise-overlay-integrator`.
  - Allowed files: enterprise Workbench/Manager/Data Engine assembly, enterprise build metadata, enterprise deployment templates, and tests.
  - Out of scope: deleting enterprise auth/permission code, adding M3 deployment, changing unrelated enterprise features.
  - Dependencies: tasks 2.1-2.3.
  - Focused verification: enterprise app assembly, server-auth-boundary, permission, license, and adapter-discovery tests.
  - Broader verification: `pnpm typecheck`, `pnpm test`, `pnpm test:scripts`, and `pnpm lint` in the enterprise worktree.
  - Handoff evidence: final source ownership map, enterprise boundary test output, and build configuration diff.

## 4. Build and validate enterprise artifacts

- [x] 4.1 Build the three enterprise images in real-license mode, run image and Compose smoke checks, and record immutable image references; verify PostgreSQL and Doris paths work without M3.

  - Evidence: real-license builds and image checks passed. Accepted image IDs are `data-agent:deploy-amd64` (`sha256:76c60905d24a7d1570e31cdfa1bcca3ab9f7fc41d52fd647e63b2695f474fd22`), `ontology-manager:deploy-amd64` (`sha256:7ddcdf561c71d8725ecf154dbbef177203a2aa47a7b72418b8d582d16792fb48`), and `datarag:deploy-amd64` (`sha256:3fc996e527810778718a58f8d9023bc594456df5dbd569b82bd9d7f717b65332`). The DataRAG image carries the `real` license label and `/app/lib/data-adapter-doris-4.1.1.jar`; no M3 runtime container or configuration is present.

  - Boundary: local/CI image builds and artifact verification.
  - Owner: `enterprise-build-verifier`.
  - Allowed files: build outputs and temporary `.build` artifacts; no source changes unless a verified failure is repaired in the owning task.
  - Out of scope: production cutover and database migration.
  - Dependencies: task 3.1.
  - Focused verification: `make data-engine ARCH=amd64 LICENSE_MODE=real PROMPT_LANG=zh`, image license check, and `scripts/registry.sh smoke amd64`.
  - Broader verification: all enterprise typecheck/test/lint/script checks and image label/digest inspection.
  - Handoff evidence: image digests, build logs, smoke result, and list of enabled runtime adapters.

## 5. Stage on the production host without interruption

- [x] 5.1 Back up the public stack configuration, PostgreSQL databases, frontend/backend data, and image identities; restore a copy into an isolated enterprise staging Compose project on `10.100.31.37` using ports 3100/3105/18187/5543; verify the public stack stays healthy and staging reaches health checks.

  - Evidence: public-stack backup and staging restore completed; the final staging checkpoint is `/home/uino/backups/enterprise-staging/20261110155601/` with verified SHA256 checksums. Staging reached healthy status on 3100/3105/18187/5543 before cutover, while the public stack remained healthy on its original ports.

  - Boundary: remote backup, staging directories, database copy, and isolated Compose runtime.
  - Owner: `staging-deployment-operator`.
  - Allowed files: `/home/uino/enterprise-staging`, deployment package/configuration, and backup destinations; never overwrite `/home/uino/ontomato` during staging.
  - Out of scope: stopping public services, deleting Docker images/cache, production cutover.
  - Dependencies: task 4.1 and verified staging image references.
  - Focused verification: `docker compose config`, `docker compose up -d --wait --wait-timeout 600`, service health, and port isolation.
  - Broader verification: PostgreSQL data checks, Doris connection/query checks, and custom-feature smoke checklist.
  - Handoff evidence: backup manifest, staging Compose config, health output, and public-stack unchanged proof.

## 6. Accept and cut over

- [x] 6.1 Execute the complete custom-feature and enterprise-boundary acceptance checklist against staging, then perform the approved production cutover with a final backup and verified rollback path; verify all required features and enterprise security behavior after switching to production ports.

  - Evidence: enterprise services now own production ports 3000/3005/18087/5432 and returned successful health checks across three consecutive probes. The public containers are stopped but retained for rollback; the original PostgreSQL volume remains intact. Post-cutover checks confirmed PostgreSQL configuration, migrated data, Doris adapter presence, real-license image identity, no M3 runtime, and verified rollback backup.

  - Boundary: acceptance, production switch, post-cutover verification, and rollback readiness.
  - Owner: `release-acceptance-controller`.
  - Allowed files: production deployment directory and release records after the cutover gate passes.
  - Out of scope: new feature development discovered during acceptance; M3 deployment.
  - Dependencies: task 5.1 and all focused/broader verification gates.
  - Focused verification: Doris, ontology version/editor, MetricView, Python formatting, login, domain, permission, license, and PostgreSQL data flows.
  - Broader verification: all four services healthy, production ports reachable, image digests match the accepted artifacts, and rollback commands are executable.
  - Handoff evidence: signed acceptance checklist, final backup location, production health output, and rollback record.

## Workflow follow-up

- Archive the completed OpenSpec change after the final audit and integration checks.
- Verify both repository integration branches contain their completed task commits and that no task worktree remains active.

# Design

## Context

The public checkout and the enterprise checkout are independent repositories. The enterprise checkout is an overlay application: its Java and pnpm workspaces consume a neighboring public source tree, while enterprise packages replace application assembly, identity, UI, and data-engine behavior where required. The production host currently runs a healthy public-edition stack with PostgreSQL product state and custom Doris-capable DataRAG images. See `proposal.md` and the delta specs for the required behavior.

## Goals / Non-Goals

**Goals:**

- Establish one matched public-source revision for the enterprise release and apply the current customization delta to that revision.
- Keep public and enterprise Git histories, build roots, and deployment artifacts separate while producing one enterprise image set.
- Preserve the current PostgreSQL product database and Doris business-data path.
- Preserve enterprise authentication, authorization, license enforcement, and domain boundaries.
- Validate with an isolated stack before production cutover and keep a tested rollback path.

**Non-Goals:**

- Do not merge the two repositories into one repository.
- Do not replace enterprise identity, permissions, licensing, or deployment assembly with public-edition files.
- Do not deploy or require an M3 service in this change.
- Do not change the public production stack before enterprise staging acceptance.

## Decisions

### 1. Use a paired two-repository build

The enterprise worktree remains the application and overlay root, and its neighboring public worktree is pinned to the public commit selected by the enterprise release process. Public customizations are applied to the public worktree; enterprise-specific assembly and integrations stay in the enterprise worktree. This matches the existing relative imports and Maven/pnpm workspace boundaries.

**Alternative rejected:** copying the public repository into the enterprise repository or merging unrelated repository histories. That would break source ownership, make future public updates difficult, and risk overwriting enterprise security boundaries.

### 2. Migrate the customization delta by module and behavior

The migration starts from the matched public baseline, then ports the current branch's Doris, ontology manager, MetricView, Python formatting, and code-editor changes. Each port is reviewed against the enterprise baseline and covered by focused tests. Public application entrypoints are not copied wholesale because enterprise entrypoints install enterprise identity, product metadata, routes, and diagnostics.

**Alternative rejected:** cherry-picking every current public commit without classification. The current branch includes build, documentation, and public assembly changes that are not safe in the enterprise overlay.

### 3. Keep PostgreSQL/Doris as the runtime contract

The product continues to use PostgreSQL for its internal state and Doris as the required business adapter. The enterprise image may contain optional adapter code required by its reactor, but no M3 service, M3 endpoint, M3 credentials, or M3 acceptance is added. The enterprise build and runtime checks must prove that M3 is not a prerequisite for health.

**Alternative rejected:** introducing M3 to exercise every enterprise adapter. That expands infrastructure and acceptance scope without serving the requested deployment.

### 4. Stage on the existing host with isolated resources

The public stack remains on ports 3000, 3005, 18087, and 5432. Enterprise staging uses a separate Compose project and ports 3100, 3105, 18187, and 5543, separate bind directories, and a restored database copy. Production data is never used as the staging rollback source.

**Alternative rejected:** in-place replacement before acceptance. It creates unnecessary downtime and makes it impossible to separate code failures from data migration failures.

### 5. Build formally, deploy immutably

Enterprise images are built with the real license mode and tested locally/CI before being imported or pulled on the host. The deployment records image digests, configuration, database backup, and the previous public stack so cutover and rollback are deterministic.

## Risks / Trade-offs

- **[Public baseline mismatch]** The current public fork is ahead of the public source currently adjacent to the enterprise checkout. → Resolve and record the enterprise `PUBLIC_SHA` before porting any code; reject an unmatched build.
- **[Enterprise security regression]** Public `index.ts`, identity, or UI assembly could overwrite enterprise behavior. → Keep enterprise assembly files as owners and port only behavior behind their interfaces; run authentication and permission regression tests.
- **[Database incompatibility]** Enterprise configuration uses different database naming and environment contracts. → Restore a database copy into staging, run enterprise migrations there, and compare key application behavior before cutover.
- **[License dependency unavailable]** The real enterprise Data Engine build requires private Maven artifacts/settings. → Treat the license artifact and settings as a preflight dependency; do not substitute development override output for production.
- **[Resource pressure]** The host has only 4 CPUs and 7.6 GiB memory. → Build outside the host, use isolated staging ports, monitor health and disk, and retain the existing stack until acceptance.
- **[Rollback data drift]** Enterprise writes after cutover may not be reversible into the old public schema. → Take a final backup, define a cutover window, and use the old stack only as rollback before accepting new enterprise writes.

## Migration Plan

1. Freeze current public image/configuration identities and server data locations.
2. Resolve and record the matched public `PUBLIC_SHA` for enterprise `release-v4.1.1`.
3. Port and test public-layer customizations in dependency order: contracts/server, Doris/Data Engine, Ontology Manager, MetricView/Python formatting, and UI/editor behavior.
4. Adapt enterprise build and deployment assembly without changing enterprise identity, permission, license, or PostgreSQL/Doris boundaries.
5. Run focused tests, broader typecheck/lint/test checks, formal real-license image builds, and image smoke tests.
6. Back up production data and restore a copy into the isolated staging stack on `10.100.31.37`.
7. Execute the custom-feature and enterprise-boundary acceptance checklist.
8. During an approved maintenance window, back up again, switch the enterprise stack to production ports, and verify health and business flows.
9. If acceptance or production verification fails, stop enterprise services and restore the previous public Compose/configuration/data state; keep the enterprise artifacts for repair.

## Open Questions

None. The target release, PostgreSQL/Doris runtime scope, M3 exclusion, paired repository structure, and staged cutover approach are fixed by this design.

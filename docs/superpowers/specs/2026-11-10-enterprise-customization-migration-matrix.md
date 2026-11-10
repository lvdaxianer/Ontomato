# Enterprise Customization Migration Matrix

Date: 2026-11-10

## Frozen source pair

| Source | Revision | Evidence |
| --- | --- | --- |
| Enterprise application | `f70a34eb9f7cb6818b3cb36c97d7fc96615998c0` (`release-v4.1.1`) | enterprise task worktree `git log -1` |
| Enterprise public dependency | `adc420b9b9edc6ed9b2177d3382e44b4027dbbe5` (`release-v4.1.1`) | `scripts/ci-public-source.sh` and GitHub `refs/heads/release-v4.1.1` |
| Current public customization | `4363fe496989441724bef484cedc69554683b652` (`features/doris`) | public task worktree `git log -1` |

The public customization head is a descendant of the enterprise public dependency:

```text
adc420b9 (matched enterprise public baseline)
        \
         ... current public customization commits ...
          \
           4363fe49 (current custom head)
```

The enterprise CI uses `PUBLIC_REF=$CI_COMMIT_REF_NAME` and checks out the same public branch or tag into `../ontomato`. The paired task worktrees reproduce that layout without changing either original checkout.

## Customization ownership

| Capability | Public customization area | Enterprise integration area | Migration rule | Verification |
| --- | --- | --- | --- | --- |
| Doris adapter | `packages/data-adapters/doris`, Data Engine core/build files | `apps/data-engine/build.mjs`, enterprise reactor | Keep Doris in the public source set and include it in the enterprise build; do not add M3 runtime | Doris adapter tests, adapter discovery, read-only FE verification |
| Ontology revisions | Data Engine revision controllers/services/DAOs and Manager UI | Enterprise Manager host shell | Port public behavior; preserve enterprise shell/auth boundaries | Revision API/UI tests and legacy metadata checks |
| Relation join fields | Manager relation model/editor and Data Engine contracts | Enterprise Manager wrapper | Preserve `fromField`/`toField` for SQL/Doris and legacy fallback | Relation tests and Doris relation scenario |
| MetricView response | Data Engine controller/service, contracts, Workbench consumers | Enterprise Workbench assembly | Preserve cardinality-based object/array/empty shape and completeness metadata | MetricView execute/testExecute tests |
| Python formatting | Data Engine controller/service, Workbench API/editor | Enterprise Workbench assembly | Preserve endpoint contract and enterprise request identity | Python format tests and authenticated endpoint check |
| Code editor/cache | Public Ontology Manager components and build/cache handling | Enterprise Manager host and image build | Port UI behavior without replacing enterprise host integration | Component tests, Manager build, browser smoke |
| Chinese/English resources | Public Manager/Workbench locale resources | Enterprise locale and content installation | Preserve public terminology behavior while keeping enterprise locale authority | Locale and enterprise shell tests |
| Enterprise authentication | Not copied from public edition | `packages/workbench-server-enterprise`, enterprise UI auth | Enterprise implementation remains authoritative | Auth boundary, permission, SSO/LDAP/API-key tests |
| License/deployment | Not copied from public edition | Enterprise Data Engine license and `deploy/` | Use real license build and Harbor/enterprise deployment path | License jar check, image smoke, deployment health |

## Explicit exclusions

- No M3 service, M3 credentials, M3 runtime configuration, or M3 acceptance gate.
- No public anonymous identity assembly in the enterprise image.
- No production cutover until isolated staging and data-copy checks pass.

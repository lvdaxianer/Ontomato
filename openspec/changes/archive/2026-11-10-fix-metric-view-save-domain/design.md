# Design

## Context

The metric-view save service receives a domain-scoped metric view and a nested
function. The controller establishes the authenticated domain on the metric
view, but the nested function may come from a browser payload without a domain
field. The DAO currently uses the nested function domain when entering the
vector retrieval gate, so a valid save can fail before the database write.

## Goals / Non-Goals

**Goals:**

- Make the save service establish one authoritative domain for the nested
  function before any function or metric-view persistence occurs.
- Reject an absent domain with a clear service error instead of a null pointer.
- Preserve existing operation checks, tenant isolation, and vector update flow.

**Non-Goals:**

- Do not change the metric-view API shape or introduce a new endpoint.
- Do not migrate existing metric-view rows or alter Doris data.
- Do not change vector embedding or retrieval algorithms.

## Decisions

1. **Normalize the nested function domain in the save service.**
   The service is the shared boundary for controller callers and owns the
   function persistence sequence. It will require the metric-view domain and
   assign that value to the nested function before `FunctionDao.save` and
   `MetricViewDao.save`. This is preferred over trusting the browser payload or
   fixing only the DAO because it preserves the authenticated domain boundary
   before any write.

2. **Use the metric-view domain as the source of truth.**
   The controller overwrites the metric-view domain from the authenticated
   user, so the nested function cannot select another tenant. A DAO fallback
   alone would hide an invalid service input and could leave the function row
   unscoped.

3. **Add a service-level regression test.**
   The test will submit a metric view with a valid domain and a nested function
   without `domainId`, then capture the function passed to the DAO. This tests
   the observable persistence boundary without connecting to production-like
   PostgreSQL or embedding services. It also covers rejecting a function ID
   that belongs to another domain before the update is written.

## Risks / Trade-offs

- [Risk] Existing callers may depend on a function-provided domain that differs
  from the metric-view domain. → The authenticated metric-view domain is the
  existing authorization boundary; the save path must not permit cross-domain
  writes, so the function is normalized to that domain.
- [Risk] A missing domain could still be passed by a non-controller caller. →
  Validate before any DAO call and return the existing localized service error
  mechanism.
- [Risk] A new image could regress other DataRAG behavior. → Run focused and
  broader Maven checks, keep the current image/config backup, and verify health,
  Doris data browsing, and metric-view save after rollout.

## Migration Plan

1. Build and test the DataRAG module from the fix branch.
2. Commit and push the fix to the public `features/doris` baseline used by the
   enterprise image build.
3. Back up the current server image reference and deployment configuration.
4. Build a new `datarag:deploy-amd64` image and recreate only the DataRAG
   service, preserving the PostgreSQL volume and domain configuration.
5. Verify health, an existing Doris data query, and metric-view save behavior.
6. Roll back by restoring the prior image/config and recreating DataRAG if any
   post-deployment check fails.


# Design

## Context

The metric view service currently returns a `JSONArray` because the execution engine can produce zero, one, or multiple result objects. The controller for `POST /metricView/execute` currently forwards that array unchanged, while the caller contract for the normal metric view endpoint expects the common one-result case as an object. The neighboring `POST /metricView/testExecute` endpoint has a separate contract and must not be changed.

## Goals / Non-Goals

**Goals:**

- Normalize only the normal metric view execution response at the controller boundary.
- Unwrap exactly one result item into `data`.
- Preserve empty and multi-item arrays.
- Prove the contract with focused controller tests.

**Non-Goals:**

- Changing the service interface or execution engine.
- Changing `metricView/testExecute` or any other endpoint.
- Changing authentication, permissions, persistence, deployment, or database behavior.

## Decisions

Use a controller-local conditional when assigning `data`: if the returned array has exactly one item, assign that item; otherwise assign the original array. This keeps the change at the HTTP contract boundary and avoids changing shared service behavior used by other callers.

The implementation will not blindly read index zero. Preserving empty and multi-item arrays avoids an index error for an empty result and prevents silently dropping data if a future metric view returns more than one result. A shared service helper was rejected because it would broaden the behavior change beyond the single endpoint requested.

The regression test will invoke the controller with mocked collaborators and verify the successful one-item response, plus the empty/multiple preservation cases. The test will not connect to a database or external service.

## Risks / Trade-offs

- [Client compatibility] Consumers that always expect an array for a one-result response must handle the corrected object shape. → This is the requested endpoint contract change and is documented in the capability spec; empty and multi-result responses remain arrays.
- [Unusual result cardinality] A multi-result response remains an array rather than being coerced into one object. → This preserves all returned data and avoids a lossy compatibility shortcut.

## Migration Plan

Deploy the controller change with the application build. Rollback is a normal application rollback because no schema or persistent data changes are involved.

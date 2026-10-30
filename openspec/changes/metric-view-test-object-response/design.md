# Design

## Context

`MetricViewService.execute` and `MetricViewService.testExecute` both return an `Object[]` whose first element is a `JSONArray` of result objects. The controller for `/metricView/execute` already normalizes a one-item array, while `/metricView/testExecute` forwards the array unchanged. The JSON test dialog calls the latter endpoint, which explains the extra array visible in the UI.

## Goals / Non-Goals

**Goals:**

- Make both metric view execution endpoints expose the same result-cardinality contract.
- Return one result as an object, multiple results as an array, and no `data` field for zero results.
- Keep completeness metadata and all service behavior unchanged.
- Cover each cardinality with focused controller tests.

**Non-Goals:**

- Changing `MetricViewService` or its `Object[]` return type.
- Changing the frontend request or JSON rendering code.
- Changing authentication, authorization, persistence, database, or deployment behavior.

## Decisions

Use one private controller helper for both endpoints. The helper inspects only the service result payload: a `JSONArray` of size one becomes its sole item, a `JSONArray` of size zero causes no `data` field to be written, and any multi-item array or non-array payload is written unchanged. Keeping this normalization at the HTTP boundary avoids changing other service callers and prevents one endpoint from drifting from the other.

The helper will run before `PythonExecuteResult.markCompleteness(ret)`, so existing completeness metadata is preserved regardless of whether `data` is present.

## Risks / Trade-offs

- Existing callers of `/metricView/testExecute` that assume a one-item array must accept the corrected object shape. The visible JSON test already expects the same shape as normal execution.
- Empty responses no longer carry an empty array under `data`. The caller already treats missing `data` as an empty display value, and this is explicitly allowed by the requested contract.

## Verification

The focused `MetricViewControllerExecuteTest` will cover one, multiple, and zero results for both endpoints where relevant. The module test command and `git diff --check` will provide broader compile and whitespace verification.

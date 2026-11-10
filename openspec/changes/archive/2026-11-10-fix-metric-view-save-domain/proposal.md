# Proposal

## Why

Saving an editable metric view can fail when the request contains a function
without its nested `domainId`. The controller already establishes the
authenticated domain on the metric view, but the save path later reads the
domain from the nested function and passes null into vector retrieval, causing
a production `NullPointerException` and the generic “request failed” message.

## What Changes

- Propagate the authenticated metric-view domain to the nested function before
  persisting the function and metric view.
- Add a regression test for a save request whose nested function has no domain.
- Preserve the existing authorization and metric-view persistence behavior.

## Capabilities

### New Capabilities

- `metric-view-save-domain-isolation`: Metric-view saves keep nested function
  persistence and vector updates within the authenticated domain.

### Modified Capabilities

None.

## Impact

- `packages/data-engine-core` metric-view save service and tests.
- Existing `POST /metricView/save` callers gain reliable handling for payloads
  that omit the nested function domain.
- The deployed DataRAG image must be rebuilt and rolled out; no database data
  migration is required.

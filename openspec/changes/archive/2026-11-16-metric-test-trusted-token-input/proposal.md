# Proposal

## Why

Metric-view Python programs use the `REQUEST_TOKEN` runtime placeholder, but the
Ontology Manager test dialog currently sends only the normal session credential.
The backend therefore cannot resolve `X-Trusted-Token`, so a published metric
view cannot be tested from the UI even though the trusted-token deployment is
configured.

## What Changes

- Add a masked trusted-token field to the metric-view test dialog.
- Send the entered token only as the `X-Trusted-Token` request header for the
  test execution request.
- Keep the token in component memory only and clear it when the dialog closes.
- Never include the token in the JSON request body, metric-view source, browser
  storage, logs, or user-facing error messages.
- Preserve the existing backend proxy and runtime-variable resolution contract.

## Capabilities

### New Capabilities

- `metric-view-test-credentials`: Allow an authorized user to provide a
  one-time trusted token for metric-view test execution without persisting it.

### Modified Capabilities

None.

## Impact

- Public Ontology Manager request client and metric-view test dialog.
- Public Ontology Manager API and component tests.
- No enterprise Java or proxy source changes; the existing proxy already
  forwards `X-Trusted-Token`.
- The assembled enterprise frontend must be rebuilt and deployed after the
  public package change.

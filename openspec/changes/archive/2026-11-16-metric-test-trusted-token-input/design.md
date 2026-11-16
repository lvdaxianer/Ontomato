# Design

## Context

The runtime-variable registry intentionally keeps `REQUEST_TOKEN` as request
context data. The Manager API client currently constructs credential headers
from the host session credential only, while the metric test dialog has no way
to provide the separate trusted token.

## Decisions

1. Add an optional per-request `trustedToken` option to the Manager client
   rather than adding it to `ManagerCredential`. A trusted token is an
   execution credential, not the user's login identity.
2. Add a password-style input to the metric test dialog. The value is held in
   reactive component state, is never serialized into the request body, and is
   cleared on dialog close and after the request completes.
3. Require a non-blank token before JSON or Markdown metric tests run. The
   existing backend error remains the source of truth for invalid or expired
   tokens.
4. Keep the current proxy unchanged because it already forwards an incoming
   `X-Trusted-Token` header to DataRAG.

## Request Shape

The Manager client continues sending the existing body. It adds this header
only when the test call supplies a non-blank token:

```http
X-Trusted-Token: <temporary token>
```

The token is not sent as JSON and is not part of the persisted metric-view
definition.

## Verification

- Unit-test the client header and body behavior.
- Test the dialog's required-token validation, request invocation, and cleanup.
- Run the focused Ontology Manager test suite, typecheck/build, and whitespace
  checks.
- Deploy the rebuilt frontend and verify the live metric test returns JSON.

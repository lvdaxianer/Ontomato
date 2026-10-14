## Why

The real Doris FE accepts the existing MySQL wire protocol, but the current
integration probe can be routed through a developer-machine SOCKS proxy and the
connection report exposes the JDBC compatibility product as `MySQL`. That made a
valid Doris connection either fail before the handshake or fail the real test
before it exercised an actual SELECT.

## What Changes

- Make the real-FE test execution explicit about bypassing proxy routing for
  private FE addresses while preserving caller-supplied JDBC URL options.
- Make the connection report identify the adapter as Doris while retaining the
  JDBC compatibility details for diagnostics.
- Strengthen the opt-in real FE test so it verifies metadata discovery and a
  live read-only Ontology DSL SELECT with a read-only account.
- Keep write operations unsupported and do not add data replication or CDC.

## Capabilities

### New Capabilities

- `doris-jdbc-connectivity`: Establish and verify direct, read-only JDBC access
  to a Doris FE using the configured URL and credentials.

### Modified Capabilities

## Impact

- Affected module: `packages/data-adapters/doris`.
- Affected behavior: Doris adapter connection setup and connection-test report.
- No schema, runtime data, remote repository, or deployment secret changes.

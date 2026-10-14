## Context

The Doris adapter uses the MySQL wire protocol and MySQL Connector/J. JDBC
therefore reports the compatibility product as `MySQL`, even though the adapter
is connected to Doris. The opt-in real-FE test currently treats that protocol
name as a failed connection. During local verification, the JDK also inherited a
SOCKS proxy whose CIDR-style bypass list did not match the private FE address;
the test process must explicitly disable that proxy for a direct FE test.

## Goals / Non-Goals

**Goals:**

- Report a successful Doris adapter connection as Doris while preserving the
  underlying JDBC product for diagnostics.
- Verify the real FE test reaches metadata discovery and a live read-only DSL
  SELECT.
- Keep all production queries read-only and leave write methods unsupported.

**Non-Goals:**

- Do not mutate global JVM proxy settings from the adapter.
- Do not add a proxy, data copy, CDC job, synchronization task, or write path.
- Do not change the Doris JDBC protocol or replace MySQL Connector/J.

## Decisions

1. The adapter report will use the adapter identity `Doris` for
   `databaseProduct` and expose the JDBC compatibility value separately as
   `jdbcDatabaseProduct`. This avoids presenting a protocol implementation
   detail as the configured data-source type.
2. The real-FE test will be run with JVM proxy properties explicitly cleared in
   environments where a system SOCKS proxy is configured. Proxy routing is an
   execution-environment concern and must not be changed globally by a data
   adapter.
3. The existing metadata and DSL SELECT paths remain the verification surface;
   no test will create, alter, insert, update, or delete Doris data.

## Risks / Trade-offs

- [Risk] Existing callers may inspect the database product field as a JDBC
  product. → Mitigation: retain that value in `jdbcDatabaseProduct` and keep the
  compatibility version and driver fields unchanged.
- [Risk] A developer may run the real test through an active SOCKS proxy. →
  Mitigation: document and use explicit Surefire JVM properties in the test
  command; production deployments must provide their own network routing.

## 1. Implementation

- [x] 1.1 Add separate Doris adapter and JDBC compatibility identities to the
  connection report.
- [x] 1.2 Update unit and opt-in real-FE tests to cover the report contract and
  live read-only metadata plus DSL SELECT behavior.

## 2. Verification

- [x] 2.1 Run focused Doris tests with the JVM SOCKS proxy disabled and the
  supplied FE endpoint.
- [x] 2.2 Run the applicable module and assembly checks, then review the diff
  for secrets and unintended writes.

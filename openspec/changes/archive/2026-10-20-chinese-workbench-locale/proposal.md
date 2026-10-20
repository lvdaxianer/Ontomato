# Proposal

## Why

The public Ontomato workbench on port 3000 currently hard-codes English as its
only locale, while the Ontology Manager on port 3005 is already Chinese-first.
This creates an inconsistent product experience for Chinese users and makes the
public deployment diverge from the expected company-facing language behavior.

## What Changes

- Add Simplified Chinese message resources for the workbench UI and its
  locale-sensitive workbench server messages.
- Make Simplified Chinese the default locale for the standalone workbench.
- Keep English available as an explicit selectable locale.
- Send the selected workbench locale to the data-engine service so generated
  status, error, and query messages follow the same language where supported.
- Preserve fixed English protocol, matcher, and historical diagnostic text
  whose language is intentionally stable for compatibility.
- Preserve host-controlled locale behavior for embedded workbench usage.
- Add focused tests for locale defaults, switching, resource coverage, and
  request-language propagation.

## Capabilities

### New Capabilities

- `workbench-localization`: Provide a Chinese-first standalone workbench with
  English selection and locale propagation across visible workbench messages.

### Modified Capabilities

## Impact

- `apps/workbench` standalone i18n configuration, runtime defaults, and tests.
- `apps/workbench/web` standalone language options.
- `packages/workbench-ui` browser message resources and locale behavior.
- `packages/workbench-server` shared locale and application-message resources.
- No database schema, public API shape, data-adapter behavior, or deployment
  topology changes.

# Proposal

## Why

The standalone open-source Ontology Manager currently registers only English
messages, while the company deployment uses Chinese terminology and workflows.
This makes the same product look inconsistent and increases the risk that users
misread ontology fields, relation definitions, and operation feedback.

## What Changes

- Add a complete Simplified Chinese message resource for all current Manager
  message keys, including shared workbench text and Element Plus widgets.
- Make `zh-CN` the default locale for the standalone Ontology Manager while
  retaining English as an explicitly selectable locale.
- Keep the host-supplied message contract intact so embedded deployments can
  continue to provide their own locale resources.
- Add tests that verify Chinese defaults, English selection, fallback behavior,
  and representative Manager screens.
- Rebuild and redeploy only the Ontology Manager image after verification.

## Capabilities

### New Capabilities

- `ontology-manager-localization`: Provide consistent Simplified Chinese and
  English UI resources and locale selection for the Ontology Manager.

### Modified Capabilities

None.

## Impact

- `packages/ontology-manager` receives the Chinese message resource and locale
  coverage tests.
- `apps/ontology-manager` changes its standalone locale profile and startup
  selection tests.
- No API, database, adapter, ontology data, or permission behavior changes.
- The deployed artifact is the Ontology Manager frontend image only.

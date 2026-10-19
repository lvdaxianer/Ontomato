# Design

## Context

The Manager package already separates message keys from components and accepts
the current locale and message bundle through `ManagerHostContext`. The
standalone shell currently registers only `en` in its profile, while Element
Plus is configured from the same host-provided bundle.

## Goals / Non-Goals

**Goals:**

- Add a complete `zh-CN` bundle with terminology aligned to the company UI.
- Make `zh-CN` the standalone default while preserving explicit English use.
- Keep host-provided locale resources authoritative for embedded instances.
- Validate translation coverage and representative rendered UI behavior.

**Non-Goals:**

- No changes to APIs, database records, adapter behavior, permissions, or
  ontology semantics.
- No private enterprise source, history, or runtime configuration enters the
  public repository.
- No automatic translation service or runtime network dependency.

## Decisions

### Use the existing typed message contract

Add a Chinese resource with the same shape as `englishMessages` and shared
messages. This keeps components locale-agnostic and lets TypeScript expose
missing keys during maintenance. Hard-coding Chinese strings in Vue templates
would make company terminology drift harder to detect.

### Default only the standalone shell to Chinese

Register both `zh-CN` and `en` in the standalone profile and set `locale` to
`zh-CN`. The shell's existing URL, storage, and host-message selection remains
the mechanism for opting into English or another supplied locale. Embedded
hosts continue to pass their own bundle through the existing context contract.

### Reuse Element Plus locale resources

Set the Chinese Element Plus locale in the Chinese bundle and retain the
existing English Element Plus locale. This keeps built-in form validation,
pagination, date, and table controls consistent with Manager-owned text.

### Test at resource and shell boundaries

Add a resource coverage test that compares the Chinese key shape with English,
an i18n test for fallback and interpolation, and shell tests for the Chinese
default and explicit English selection. Keep the existing component tests to
verify host-supplied messages still win.

## Risks / Trade-offs

- [Risk] A translation key added later may be missing from Chinese → a key-shape
  test fails in CI, and the runtime fallback prevents a blank UI.
- [Risk] Changing the standalone default affects screenshots and existing users
  → English remains available through the supported locale selection path.
- [Risk] Company terminology may differ from literal translations → use the
  company UI vocabulary for domain terms and keep technical identifiers such
  as API names and SQL fields unchanged.

## Migration Plan

1. Add the Chinese resource and locale registration.
2. Run Manager tests, type checks, and production build.
3. Build and validate the amd64 Manager image.
4. Preserve the current server image as a rollback tag and recreate only the
   Manager service.
5. Verify Chinese static assets and retain the existing Nginx timeout template.

Rollback restores the saved Manager image tag and recreates only the Manager
service; other services and persisted metadata are untouched.

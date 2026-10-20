# Design

## Context

The standalone workbench currently installs a single server locale pack with
`defaultLocale: "en"`, disables switching, and exposes only English in the web
business configuration. The browser package already models multiple supported
locales, but the OSS entry point provides only English messages and English
Element Plus controls. Data-engine request headers also use the OSS runtime
fallback rather than the active workbench locale.

## Goals / Non-Goals

**Goals:**

- Make Simplified Chinese the standalone default for browser-visible workbench
  text and locale-sensitive workbench server text.
- Keep the existing English resource and make English explicitly selectable.
- Keep locale ownership at the host boundary for embedded usage.
- Propagate the active locale to data-engine requests without changing request
  payloads, response shapes, or business behavior.
- Verify resource registration and the default/switch/request contract with
  focused tests.

**Non-Goals:**

- No changes to Ontology Manager locale behavior.
- No changes to public API paths, database schemas, adapters, or query logic.
- No automatic machine translation at runtime and no new translation service.
- No translation of the fixed `appTextLocale` pack, which contains protocol,
  matcher, and historical diagnostic text that must remain stable.

## Decisions

### Use checked-in locale packs

Add a checked-in `zh-CN` browser pack and workbench-server pack alongside the
existing English packs. This keeps rendering deterministic, works offline in
the deployed image, and lets missing-key tests detect drift. Runtime fallback
remains the configured default rather than silently loading a third-party
translation source.

### Configure Chinese-first OSS defaults while retaining English

The OSS i18n entry point will register both `zh-CN` and `en`, set `zh-CN` as
the default locale, keep the fixed app-text locale as `en`, and enable
switching. The shared i18n package remains host-driven; only the standalone OSS
entry point chooses these defaults. The backend-language selector on the system
configuration page remains independent because it represents the languages
actually shipped by the data engine, not the browser workbench locale.

### Derive request language from the active locale

The workbench API client will use the installed locale when constructing the
`Accept-Language` header, falling back to the OSS runtime default only before
the locale is available. This changes only language negotiation; it does not
alter endpoint selection, authentication, timeout, or query payloads.

### Preserve embedded-host authority

No shared package will force a locale or overwrite host-provided resources.
The standalone entry point supplies its own defaults, while embedded callers
continue to install their own language profile and server packs.

### Alternatives considered

- **Chinese-only build:** simpler, but removes the existing English workflow
  and makes the public product less compatible with existing users.
- **Change only the default locale without resources:** would leave English
  strings visible and make the requested language claim false.
- **Runtime translation service:** adds deployment and latency dependencies and
  is unsuitable for deterministic product UI text.

## Risks / Trade-offs

- [Resource drift] New English keys can be added without Chinese translations
  → add a resource-shape test and keep fallback behavior safe.
- [Longer bundle] Registering two packs increases the browser/server bundle
  size → reuse the existing pack split and avoid adding dependencies.
- [Backend locale mismatch] Some data-engine deployments may not have a
  Chinese pack → send the locale through the existing negotiation header and
  retain the service's documented fallback behavior.

## Migration Plan

1. Add and test the Chinese resources and locale configuration.
2. Build and run the existing workbench checks.
3. Rebuild only the workbench deployment image and restart the port-3000
   service; leave DataRAG, Ontology Manager, and PostgreSQL unchanged.
4. Verify the browser default, English switching, and request header in the
   deployed service. Rollback by restoring the previous workbench image if a
   deployment check fails.

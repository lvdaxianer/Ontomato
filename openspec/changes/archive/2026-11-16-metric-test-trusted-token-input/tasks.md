# Tasks

- [x] T1. Add transient trusted-token input and per-request header support,
  update localization, add focused API/component tests, run verification, and
  record deployment evidence.

  Evidence: public ontology-manager tests (116 passed), typecheck, ESLint,
  strict OpenSpec validation, and enterprise ontology-manager typecheck/build
  passed. Deployed the Workbench frontend and Manager static frontend to
  10.100.31.37; the live metric test accepted the request-scoped token and
  cleared it after a successful Markdown result. Release:
  `ontomato-20261116155531-55e7cc8-004`.

# Proposal

## Why

The production deployment currently contains public-edition customizations for PostgreSQL/Doris data access, ontology version management, MetricView responses, Python formatting, and the ontology code editor. The target enterprise edition must retain those behaviors while adding enterprise authentication, authorization, licensing, and deployment capabilities without replacing the current data or silently dropping custom features.

## What Changes

- Establish the enterprise `release-v4.1.1` application as the deployment baseline and pair it with the exact public source revision required by its build.
- Port the current public-edition customization delta into the matched public source and enterprise overlay without merging the repositories or replacing enterprise identity, permission, license, or deployment assembly.
- Build the enterprise `data-agent`, `datarag`, and `ontology-manager` images with PostgreSQL as the product database and Doris as the required business-data adapter.
- Preserve the existing Doris connection identity, read-only verification, SQL relationship join fields, MetricView cardinality-based responses, Chinese/English Manager behavior, ontology version tooling, Python formatting, and code-editor behavior.
- Exclude M3 runtime deployment and M3 acceptance from this change; M3 remains an optional future adapter.
- Validate the resulting images in an isolated staging stack on `10.100.31.37`, migrate a verified copy of the existing data, and switch production only after custom-feature and enterprise-boundary acceptance passes.

## Capabilities

### New Capabilities

- `enterprise-customized-deployment`: Build and deploy an enterprise edition that includes the approved public-edition customization set while preserving enterprise authentication, authorization, licensing, and PostgreSQL/Doris runtime boundaries.

### Modified Capabilities

- `doris-jdbc-connectivity`: The Doris connectivity behavior must remain available in the enterprise data-engine image and support the existing read-only verification contract.
- `metric-view-execute-response`: The enterprise Workbench/DataRAG combination must retain the existing normalized MetricView response shape.
- `ontology-manager-localization`: The enterprise Manager shell must retain the established Chinese/English locale behavior while using the enterprise host integration.
- `ontology-relation-join-fields`: The enterprise Manager must retain SQL/Doris relation join-field behavior.

## Impact

- Public source modules: contracts, workbench server/UI, ontology manager, data-engine adapters, and their focused tests.
- Enterprise source modules: workbench assembly, enterprise server/UI packages, enterprise data-engine build configuration, and deployment packaging.
- Build dependencies: the matched public source revision, enterprise license Maven artifacts, Node/pnpm dependencies, and architecture-specific image builders.
- Runtime systems: the current PostgreSQL data, frontend/backend runtime directories, Docker Compose project, and staging/production port assignments on `10.100.31.37`.
- Safety boundary: no M3 service is added; existing production services remain available until the enterprise staging stack passes acceptance and a rollback point is recorded.

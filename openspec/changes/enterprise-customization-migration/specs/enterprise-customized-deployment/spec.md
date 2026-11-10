# Spec Delta

## Purpose

Provide a repeatable enterprise deployment that preserves approved public-edition customizations while enforcing enterprise identity, authorization, licensing, and PostgreSQL/Doris runtime boundaries.

## ADDED Requirements

### Requirement: Enterprise images include the approved public customization set
The enterprise build SHALL include the approved public-edition customization set in the workbench, data engine, and ontology manager images, and SHALL preserve enterprise authentication, authorization, licensing, and deployment assembly.

#### Scenario: Customized enterprise images are assembled
- **WHEN** the enterprise build is run with its matched public source revision
- **THEN** the resulting images expose the approved Doris, ontology manager, MetricView, Python formatting, and code-editor behaviors
- **AND** enterprise identity and permission boundaries remain active

### Requirement: PostgreSQL and Doris are the required runtime data paths
The enterprise deployment SHALL use PostgreSQL for product state and SHALL support Doris as a business-data adapter without requiring an M3 service or M3 runtime configuration.

#### Scenario: PostgreSQL product state and Doris business data are available
- **WHEN** the enterprise stack starts with its PostgreSQL database and a configured Doris data source
- **THEN** product state is readable from PostgreSQL and ontology queries can use Doris
- **AND** the stack does not require an M3 service to become healthy

### Requirement: Enterprise cutover is preceded by isolated validation
The deployment process SHALL validate the enterprise stack with isolated ports and copied data before replacing the running public-edition stack, and SHALL retain enough configuration and data state to restore the previous deployment.

#### Scenario: Staging validation does not interrupt production
- **WHEN** the enterprise staging stack is started while the public stack is healthy
- **THEN** the public stack remains available on its existing ports
- **AND** the enterprise stack uses separate runtime directories and database state

#### Scenario: Failed cutover can be rolled back
- **WHEN** enterprise acceptance fails during or after production cutover
- **THEN** the previous public stack configuration and data can be restored without using the enterprise database as the rollback source

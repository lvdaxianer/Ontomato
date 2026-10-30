# Metric View Test Object Response Design

## Problem

The Ontology Manager JSON test dialog calls `/metricView/testExecute`, which returns a one-item array even though `/metricView/execute` already returns the single metric result as an object. This makes the same metric view appear with different JSON shapes depending on the execution path.

## Decision

Normalize both controller responses at the HTTP boundary with one shared helper. A single result is returned as an object, multiple results remain an array, and an empty result omits `data`. The service return type and frontend request flow remain unchanged.

## Verification

Focused controller tests cover the three result cardinalities and preserve completeness metadata. Maven module tests and `git diff --check` provide broader verification.

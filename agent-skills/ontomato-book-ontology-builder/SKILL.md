---
name: ontomato-book-ontology-builder
description: "Derive a book-specific ontology from full reading, with precise properties, source evidence and meaningful analysis; deliver one ontology Excel, one Markdown design guide and a ready-to-deploy Ontomato handoff package. Use with ontomato-ontomap-builder when the result must answer questions, not for ordinary book summaries."
---

# Book ontology builder

Explain what makes this book distinctive through a sourced, queryable model. The accepted version produces **one ontology Excel, one readable Markdown design guide and, when the result must answer questions, one Ontomato handoff package**. All required workflow rules are here; use the [guide template](#markdown-guide-template) when writing the Markdown. No bundled scripts, CLI or historical catalogues are required.

## Ontomato: work through one ontology

Ontomato is an ontology platform: it describes a domain and uses that shared meaning to connect data and answer questions.

| Concept | Meaning |
| --- | --- |
| Class and object | A class describes an object type; an object is one instance, identified by its primary-key value. |
| Attribute | A named property with a type and business meaning. Display names and descriptions explain a field; they do not replace its technical name. |
| Relationship | A directed connection with one meaning, such as Character **belongs to** Faction. Direction is part of the meaning. |
| ontomap | The machine-readable definition of classes, attributes, relationships and their data mappings. It describes the data; it is not the records themselves. |
| Evidence | The exact source passage that supports a value. In Ontomato it is stored as a `vector` attribute so the passage can be retrieved semantically. |
| Analysis | A sourced judgment about a record: conclusion, reason, limits and method. It is data, not commentary kept outside the workbook. |

This skill works in two layers and keeps them distinct:

- **Semantic layer** — the Excel workbook and design guide. Full fidelity: book-specific classes, qualified references, evidence and analysis.
- **Runtime layer** — the ontomap. A declared, deliberately lossy projection of the semantic layer that a data source can actually serve.

Never present the runtime layer as if it preserved everything. Every downgrade is recorded in the projection register (§7).

## This skill's role

Own the path from source text to an accepted book ontology: read the declared scope, derive the smallest useful model, keep evidence inside the ontology, include analysis, deliver the workbook and guide, and prepare the handoff package when questions must run against a deployed Ontomato.

This skill does **not** connect data sources, create business tables, replace an ontomap, upload resources or run questions. Those are deployment actions with their own authorization and verification rules; `ontomato-ontomap-builder` owns them. This package ships no `ontomato.mjs` and no `application.yml`; do not vendor a second client.

## Prerequisites

- The authoritative book files and the requested milestone.
- One recording language for names and prose, confirmed once per project.
- For a queryable result: a deployed Ontomato, an authorized data source, and the `ontomato-ontomap-builder` package. Without them, deliver the semantic layer and state that deployment is pending.

Reading and modeling do not require a deployment. Start with the book; ask for environment details only when the handoff actually begins.

## 1. Establish scope and read

- Identify the authoritative source, edition, volumes and requested milestone. Confirm the recording language once per project unless already explicit; apply it consistently while preserving original quotations and stable IDs. Reading can proceed while the language choice is pending.
- Preserve the original source and its hash. Check completeness, encoding/OCR, chapter order and meaningful material such as notes, tables and images. Record necessary normalization and unreadable content. Freeze the prepared text with stable chapter/paragraph locators; changed text requires affected span checks.
- Read the entire declared scope continuously from the beginning for an initial ontology, a redesign that replaces its central modeling questions, or an explicit fresh read or independent fresh verification. Collect provisional observations, then finalize the model. Summaries, search hits and old databases cannot substitute for this pass.
- Track reading separately from extraction; resume interruptions at the saved location. For follow-ups, new properties, local repairs and exports, reuse a reliable completed pass and existing data while reviewing affected source passages and dependencies. Read newly added source scope fully. For source changes, review changed passages and dependencies; reread the full scope when changes cannot be bounded reliably or prior reading coverage is unreliable. Never claim reused reading was newly performed.

## 2. Derive the smallest useful model

Start with a few book-specific questions and source-supported mechanisms, distinctions or transformations. Explain their centrality and how actual fields and directed joins answer the questions. For nonfiction, preserve the book's concepts, arguments, premises, objections, methods and reported observations. Do not impose a stock narrative catalogue or universal Event class.

Export only classes/properties that answer a book question, connect records, qualify an answer or verify evidence. Keep extraction decisions, review status, repair history and ID migration logs in the existing project review. Carry their substantive findings into the appropriate facts, relationships or judgments.

Keep one authoritative schema/dictionary:

- **Class:** name, plain definition, what one row represents, identity/boundary and membership rules, nearby exclusions, source examples and why this book needs it.
- **Property:** exact owner/key, question and meaning, value kind (fact, attributed claim, interpretation, derived or structural), type/cardinality, relevant vocabulary/unit, source cues, filling and missing-value rules, scope and evidence requirements. Check material rules on contrasting source cases; avoid generic boilerplate.
- **Reference:** endpoint classes/keys, direction, cardinality and qualifiers. Name the connection as a meaningful statement: source → relationship phrase → target. Define polymorphic discriminators and predicate-specific endpoint constraints.

Prefer properties or typed references on the correct scoped object. Create a separate relationship or assessment class only when independently varying occurrences, viewpoints, periods or methods require their own identity; explain what existing properties cannot preserve. Column count alone is not a reason.

Split parts or phases only when both a source-supported boundary and an independently useful distinction exist. Keep classification, composition, participation, ownership and membership distinct. Parent/subclass memberships share canonical identities and satisfy inherited rules; do not count them as separate occurrences. Apply consistent grain to comparable cases.

Make important filters, changes and joins structured; incidental context can remain supported prose. Preserve uncertainty, native quantities/units and time precision. Separate occurrence time from reporting time, attributed belief from established fact, intention from execution, capability from use, and immediate outcome from final objective. Later revelations do not give characters earlier knowledge. Do not invent reciprocal links, causal continuity, unnamed identities or considered alternatives.

Before bulk population, fix class identity: every class needs one stable, text-valued primary key that survives re-sorting and re-extraction, and every physical name must be a safe data-source identifier. §7 defines the naming and projection rules; deciding them late forces an ID migration.

## 3. Keep evidence inside the ontology

Every workbook includes **Evidence Passage** and **Evidence Support** as actual class sheets, with localized names allowed.

An Evidence Passage identifies a complete contextual paragraph or contiguous paragraph group in the source, reconstructed from segments when necessary. Preserve exact wording, punctuation, whitespace, paragraph boundaries, source identity and locators. Reuse the same span rather than duplicating it. Declare its content as `vector`, while storing the complete original text in Excel; this does not generate or upload embeddings. Never substitute a summary, clipped quote or invented text. Split oversized spans at paragraph boundaries; if a single paragraph still exceeds cell capacity, use ordered, nonoverlapping segments retaining its source/paragraph ID and character offsets. Link supports to the complete logical passage, keep all segments in Excel, and verify their reassembly from exported cells exactly matches the source.

Each Evidence Support row identifies its own ID, target class/record, exact target property, passage, support role and necessary qualification (such as time, claimant or particular value). Leave the property blank only for a genuinely whole-record assertion. Distinguish support, contradiction and context: a record-level passage link does not prove every property, and context is not positive support.

Review each material value against full passages for identity, attribution, timing, scope and meaning. Evidence that someone made a claim does not establish the claim's truth. Where speech is modeled, preserve speaker continuity, nested quotation and speech/thought distinctions; account for relevant expression candidates without inventing turns at each paragraph break. Noncontiguous spans need an explicit assembly rule.

**Evidence Support is an independent class by default.** It carries the per-property provenance that a passage-level link cannot express. Downgrade it to a single `evidence_id` field on each target class only when table count or upload volume is a stated constraint; record the lost `target_property` dimension in the projection register. Never drop it silently.

## 4. Include meaningful analysis by default

Ontology creation and full extraction include source-grounded local analysis unless the user requests factual-only work. Do not wait for another analysis prompt. Explain choices, mechanisms, tradeoffs or limits beyond paraphrasing facts; general class-design explanations do not substitute for analysis of actual records.

For each substantive class, identify useful analytical questions and their eligible scope. If analysis adds no value or lacks support, give a class-specific reason. If the question is handled elsewhere, identify the owning class/property and a demonstrated record or query. A repeated blanket exclusion is insufficient; separate analysis columns are not compulsory for every class.

Each accepted judgment must retain its **conclusion, case-specific reason, material assumptions/limits, precise target/property, applicable method and qualified evidence** in the workbook. Document shared methods once in visible property metadata; retain record-level method distinctions only when they affect interpretation. Keep multiple dimensions' reasons distinguishable and use the appropriate time/viewpoint. Preserve disagreement and unknowns; do not turn an attributed interpretation into an unqualified fact.

Numerical scores are optional. Use them only when degrees answer a meaningful question and evidence supports that precision. Analytical scores use the full **0–100** range, with every integer available rather than a few permitted anchors. Define the construct, comparison scope, endpoints, intermediate guidance, source cues and unknown handling; justify each value. Unknown is null, not zero or 50. Preserve source numbers in native units. Derived results declare inputs, formula, counting unit, denominator and missing-value handling; do not average incompatible dimensions or force a distribution.

## 5. Review coverage and completion

Use one compact project review, linked to definitions and evidence. For each central question or pattern, record the representation, query path, tested records, findings and limits. Distinguish **schema expressibility, sample population, extraction coverage and analytical coverage**. Record semantic verdicts from full-context source review; never infer “reviewed”, “supported” or zero pending from field presence, evidence existence, valid IDs or default flags. Unreviewed values remain pending.

**First backbone:** after full reading and before bulk population, present the scoped questions, classes/properties and connected source-backed examples to the user. Demonstrate a central query, a contrasting case and a meaningful unknown when available, using saved sample rows. Normally include 3–4 real instances per included class and the evidence/targets needed for closed joins; explain genuine scarcity, never invent or split records for a quota. Report **full first read / sample-populated backbone**; additional population may remain pending, but missing central demonstrations, unsupported values, essential missing analysis and broken joins block acceptance. Continue automatically if full extraction is authorized unless the user requests a review pause.

**Full extraction:** revisit the source systematically under the accepted model. In the project review, record reviewed/excluded/pending dispositions and corresponding records for every paragraph, including reviewed paragraphs that yield no records.

Apply these checks to the declared milestone:

1. **Verify blanks and their explanations.** Read relevant full passages, surrounding context, later clarifications and neighbouring fields. Check claims such as “not stated” or “no later occurrence” against the property's source/time scope, including aliases and related records. If prose supplies a missing structured value, reconcile it; create a supported target when necessary. Rejecting existing targets or writing an explanation does not prove absence. Source silence, inapplicability and genuine uncertainty may remain blank; retain concise, case-specific explanations of material blanks inside Excel. Document shared filling rules and blank conventions once in property metadata.
2. **Audit populated properties.** Reconcile material factual, attributed and analytical values by property as supported, qualified or unresolved, with counts and retrievable records in the existing review. Verify property-specific evidence supports the exact value, direction, role, time and attribution; valid IDs and exact quotations alone do not. Structural IDs and fixed metadata need no artificial citations; derived values require traceable inputs and methods.
3. **Reconcile records.** Compare structured fields, descriptions and related records, including repeated reports, later appearances, changed knowledge, outcomes and group/member counts. Check declared identity/grain and phase/viewpoint boundaries, not just unique IDs; check reference direction and target role, not just existence. Resolve contradictions or retain genuinely conflicting source claims with attribution.
4. **Test from source-derived expectations.** Select additional cases from every volume or substantive division, including a contradicting or near-miss case and legitimate unknowns. Independently establish expected values, targets, roles, state/time and claim boundaries from full source context before comparing with saved rows; never copy expected answers from the extraction. Execute property queries and actual joins against the delivered workbook; retrieving prepared prose does not demonstrate a join. Investigate unexpected agreement and uneven filling rates; counts are not proof.
5. **Close the affected scope.** For each defect, record the affected scope, correction and recheck result in the same review. Review the affected property/batch and downstream dependencies, then rerun earlier failed cases. Repairing one example or adding rows does not close the batch.

Check **analytical coverage independently**: selected questions, eligible records, completed/pending judgments and justified exclusions. Reconcile counts with retrievable records. Retrieve conclusions, reasons, limits, methods and source support; verify reasoning is case-specific and supported, rather than merely nonempty.

**Semantic acceptance gate (always):** record pass, pending, fail or justified not-applicable separately for **structure/export (§6), source/extraction coverage and analytical coverage**, with checked scope, results and remaining issues. Declare the milestone complete only when every applicable gate passes. Known omissions, unsupported material claims and unchecked material blanks block completion; verified uncertainty does not. Sample acceptance is not full extraction, and extraction completion cannot close pending analysis. New defects reopen the affected gate.

**Deployment acceptance gate (only when a queryable result was requested):** the projection package exists and passes its own checks (§7); the target domain is dedicated or explicitly cleared; `get-ontomap` was saved as a backup; `set-ontomap` succeeded and reads back; every non-empty passage has one uploaded resource; and one cross-relationship question returns a value that matches the source. A saved ontomap without a successful question is not deployment acceptance. Running the question and uploading resources belong to `ontomato-ontomap-builder`.

## 6. Deliver readable, verified files

### Excel

Use one sheet per accepted class, including evidence and any declared parents/subclasses. Each data row is an instance and each column a property. **关系表 is the sole non-class sheet**, using the confirmed language's equivalent name if needed. It maps every declared reference, including unpopulated variants, qualified/polymorphic endpoints and inheritance mappings: source class/property → relationship phrase → target class/property, with cardinality and qualifiers. Instance facts belong in class records.

| Row | Contents |
|---|---|
| 1 | Canonical property names matching the schema |
| 2 | Datatypes, including `vector` for original passage content |
| 3 | Short definitions and necessary method/interpretation guidance |
| 4 onward | Accepted instances; null remains blank |

Freeze the first three rows, wrap prose and use readable widths. Preserve IDs/source strings as text and numerical/boolean types correctly. Never interpret source text beginning with `=` as a formula. Give scalar references clickable targets and collections a clear sheet/key lookup; document lossless array/object serialization. Do not filter metadata rows as instances or truncate evidence/reasons. Keep duplicate views and helper tables outside the workbook.

### Markdown

Write for someone unfamiliar with the schema and chat. Localize and adapt the [template](#markdown-guide-template):

- Open with the purpose, why this book fits the chosen lens and concrete questions. Introduce recognizable examples before technical terms; put counts and process details later.
- Explain important choices as **book phenomenon → modeling choice → useful question**, including what separate relationship/assessment rows mean and why needed.
- Work through a central question using readable names, a supported finding, the connections that reveal it and remaining uncertainty. Then give minimal real IDs/fields for locating records, accepted reasoning and full evidence.
- Include a compact inline fenced Mermaid diagram using actual classes and labelled directed relationships. Label partial views and distinguish inheritance from composition.
- **After the diagram, include a table of every accepted class:** object class | plain-language explanation of one record | **all properties in exact Excel column order**. Include evidence/analysis classes. Explain shared ID/blank conventions once and identify the relationship map separately as a non-class sheet. Keep the explanatory prose and worked example.
- Give short navigation instructions, source/milestone and material limits. Report reading, extraction and analysis separately; distinguish supported unknowns from unfinished work. Keep detailed audits internal.

### Final checks

Apply §2's usefulness test to every exported class/property; remove bookkeeping-only content. Check keys, types, vocabularies, cardinalities, references, inheritance/identity, relevant cycles, exact evidence spans and analytical support. Compare exported class sheets, headers, types, IDs and values with the accepted data; reconcile the Markdown inventory and relationship map with the workbook. Walk an actual central query through records → property-specific support → full source, including analysis and a meaningful unknown when available. Inspect workbook presentation; preview Mermaid when a renderer is available and state any rendering limitation. Read the guide independently for clarity.

Use these results in the §5 acceptance gate. Preserve earlier releases and deliver both files together with their exact milestone, material changes and remaining limits. Successful export does not establish source or analytical acceptance.

## 7. Ontomato projection

The runtime layer must be explicitly derived, not assumed. Produce it only after the semantic layer is accepted; a model change after projection reopens the affected projection checks.

### What can and cannot be preserved

| Semantic construct | Ontomato representation | Loss / rule |
| --- | --- | --- |
| Class | One class, `name` = physical table or index name | The class name is an identifier, not prose. |
| Property | One attribute with `name`, `showName`, `desc`, `type` | Rich value kinds collapse into one column type. |
| Typed reference | Relationship with `fromClass`, `toClass`, predicate `desc`, and join fields | Only concrete, single-target pairs can be enforced. |
| Polymorphic reference | Concrete relationship per endpoint, or `documented-only` | Cannot be one relationship with a discriminator. |
| Parent/subclass | Separate flat classes, or one class with a discriminator column | Ontomato has no inheritance. |
| Multi-valued property | Child class + relationship, or serialized `text` | No array attribute type. |
| Evidence Passage | Class with a `vector` attribute holding the exact text | The Excel text is the source; the runtime stores references. |
| Evidence Support | Class with target class/ID/property columns | Target is polymorphic, so it is not a relationship. |
| Analysis judgment | Attributes on the owning class, or a separate assessment class | Only project what the questions actually use. |

If a construct cannot be represented faithfully, keep it in the semantic layer and add it to the **documented-only register**: construct, why it matters, why the runtime cannot carry it, and what a reader loses. “Not supported” without that explanation is not a projection decision.

### Naming

- `name` is ASCII snake_case, starts with a letter, uses only `a-z`, `0-9` and `_`, avoids reserved words, and is unique within the domain. It is pasted into SQL unquoted by relational adapters.
- `showName` and `desc` carry the book's language and meaning. Never put Chinese or spaces into `name`.
- Prefix every physical table with one project slug, such as `book_<slug>_`, so a shared data source cannot collide with another ontology.
- Keep a name map in the package: semantic name → physical class/attribute name → display name. Reuse it across rebuilds; a rename is an identity change, not a cosmetic edit.

### Primary key

Exactly one attribute per class is the primary key, with `type: varchar` and `enable: true`. Use a stable business or source-derived ID, never a row number or sort position. Every reference and every evidence target stores that exact value. If the source key is numeric, add a text key during import rather than changing the label.

### Relationships

- Write each relationship as `from → predicate → to`, with a verb phrase in `desc` (`belongs to`, `is located in`, `is parent of`; in a Chinese deployment `属于`, `位于`, `是…的上级`).
- Relational sources need real `fromField` and `toField` columns; the condition is `fromClass.fromField = toClass.toField`. Resolve the pair, direction and multiplicity from data, not from similar column names.
- A relationship key is a short, stable identifier; its meaning lives in `desc`.
- Split a qualified or polymorphic reference into one relationship per concrete endpoint pair, and keep the qualifier as a column on the source class. If that produces a meaningless explosion, mark it `documented-only`.
- Two relationships between the same pair need distinct predicates; do not merge them.

### Inheritance and subclasses

Flatten. Either create one class per concrete subtype with its own table and key, or one class with a `_type` discriminator and the union of attributes. Do not claim inherited constraints that the runtime cannot enforce; record which invariants moved into review checks.

### Multi-valued attributes

Use a child class with a foreign key and a relationship when the values must be queried or joined. Use a serialized `text` attribute only for display-only lists, and document the serialization. Never spread one semantic property across numbered columns (`member_1`, `member_2`).

### Evidence and vector

- **Modeling:** Evidence Passage `content` is declared `vector`; Excel holds the complete original text. No embedding is generated or uploaded in this phase.
- **Projection:** the attribute is `type: vector`; its runtime value is the text-array representation `[{"path":"...","text":"..."}]`, not a numeric vector. Do not write paths into Excel.
- **Upload:** one resource per non-empty passage, with `className`, `attrName`, `objectId` and the exact text; empty passages are skipped. Upload appends a reference, so a first upload does not require clearing `[]`, but a re-upload does. Never upload twice to “repair” an uncertain result; read the field first.
- **Verification:** read the evidence field back and confirm the reference shape; retrieval proves the resource works, a success message does not.
- **Ownership:** upload, embedding configuration and vector rebuilds belong to the deployment skill and the operator. If embedding is unconfigured or vectors need rebuilding, report it as a deployment prerequisite; do not alter the model or write vector storage directly.

### Handoff package

Create `ontomato/` beside the workbook:

| Artifact | Contents |
|---|---|
| `ontomap.draft.json` | Complete definition for the target domain: dataset description, flat classes, attributes, primary keys, relationships, vector attributes. |
| `ddl.sql` | Idempotent-enough table creation for the target adapter, with the project prefix, text primary keys, evidence tables and indexes. No destructive statements. |
| `import/<class>.csv` | One file per class, UTF-8, header row = physical names, values matching the declared types. |
| `upload-manifest.jsonl` | One line per non-empty passage: class, attribute, object ID, exact text, suffix. Order is stable. |
| `projection.md` | Name map, key choices, relationship decisions, documented-only register, evidence-class decision, open deployment prerequisites. |

Validate the package before handoff: every class in the ontomap has a table and an import file; every relationship endpoint and join field exists; every primary key is unique, non-empty text; every vector attribute has a matching manifest entry set; the documented-only register accounts for every unprojected construct. State the validation results in `projection.md`.

### Deployment boundary

Hand the package to `ontomato-ontomap-builder`; do not execute its commands from this skill.

- `set-ontomap` **replaces the whole ontomap of the domain**. It is not a merge. Before any write, read and save the current definition, and confirm the domain is dedicated to this book or explicitly cleared for replacement. Shared test environments are not safe defaults.
- Prefer a dedicated domain or a dedicated database schema. A project prefix reduces collisions but does not isolate the whole-domain replacement.
- `upload-vector` is append-only per object; re-running it without a reset duplicates references.
- `ask` is the only proof that the runtime answers from this model. Use one question that requires a relationship and one that requires evidence retrieval.
- Enterprise deployments add login and domain selection; the enterprise `ontomato-ontomap-builder` package owns those details. Never place credentials or tokens in the workbook, guide or projection package.

## 8. Continue without rebuilding

For follow-ups, test the current model/data first. Distinguish missing population, missing query, schema limitation and source uncertainty; make the smallest faithful change. Compare retained IDs and assertion meanings with the **immediately preceding published export**, including IDs first issued there. Persist newly allocated IDs independently of row/sort order; retire changed identities with explicit mappings instead of reassigning or reusing IDs. Preserve user edits and history, documenting changed meanings or language. Recheck affected evidence, definitions, queries and guide examples together. Existing project layouts need no migration merely to follow this skill.

When the runtime layer exists, also recheck the projection: name map, primary keys, relationship endpoints, evidence bindings and the documented-only register. A semantic change that alters a projected name or key requires an explicit migration note, not a silent re-export.

While work remains, keep a short `CONTINUE_HERE.md` with source/pass, accepted versus draft work, versions, next location, unresolved issues and latest checks.

Invoking the skill does not authorize unrelated project changes, delegation, uploads or background work.

## Markdown guide template

Use the following outline when creating the companion design guide. Replace its placeholders with the book-specific content required by §6. When a handoff package exists, add one short section pointing to it and state the deployment gate separately from semantic acceptance.

````markdown
# {{Book title}} — Ontology design guide

{{Purpose, why this book fits the chosen lens, and concrete reader questions.}}

[Open the ontology Excel]({{Workbook filename}})

## Why these objects and properties

{{Recognizable book examples → design choices → useful questions.}}

## A worked example

{{Question, situation, supported finding, connections and uncertainty. Follow with minimal actual IDs/fields for finding the evidence and analysis.}}

## How the model connects

{{Inline fenced Mermaid diagram of the explained path, with actual classes and labelled directed relationships.}}

## Objects and properties

| Object class | What one record represents | All properties in Excel column order |
|---|---|---|
| {{Class}} | {{Plain-language explanation}} | {{Complete ordered property list}} |

{{Shared ID/blank conventions; identify the relationship map as a non-class sheet.}}

## How to use the workbook

{{Entry point and short path through records, property support and full passages.}}

## Ontomato handoff

{{Whether a queryable result was requested; package location; what is projected and what is documented-only; deployment status and remaining prerequisites. Omit this section when no handoff was requested.}}

## Scope and limits

{{Source, milestone, separate reading/extraction/analysis coverage, remaining work and material uncertainties. Distinguish facts from analysis and explain scoring if used.}}
````

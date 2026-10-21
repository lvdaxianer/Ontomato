export type OntologyRevisionContent = Record<string, unknown>;

export type OntologyRevisionSource = "manual" | "import" | "restore" | "migration" | string;

export interface OntologyRevisionSummary {
  revision: number;
  contentHash: string;
  source: OntologyRevisionSource;
  actorId?: string | null;
  createdAt?: string | null;
}

export interface OntologyRevisionDocument extends OntologyRevisionSummary {
  formatVersion: number;
  content: OntologyRevisionContent;
}

export type OntologyConflictResolution = "current" | "incoming" | Record<string, unknown>;

export interface OntologyImportRequest {
  content: OntologyRevisionContent;
  baseRevision: number;
  baseHash: string;
  resolutions?: Record<string, OntologyConflictResolution>;
}

export interface OntologyImportConflict {
  path: string;
  baseValue: unknown;
  currentValue: unknown;
  incomingValue: unknown;
}

export interface OntologyImportPreview {
  current: OntologyRevisionDocument;
  base: OntologyRevisionDocument;
  incomingHash: string;
  mergedRule: OntologyRevisionContent;
  conflicts: OntologyImportConflict[];
}

export type OntologyDiffKind = "added" | "removed" | "changed" | "unchanged";

export interface OntologyDiffField {
  field: string;
  before: unknown;
  after: unknown;
  changed: boolean;
}

export interface OntologyDiffItem {
  key: string;
  label: string;
  kind: OntologyDiffKind;
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
  fields: OntologyDiffField[];
  children?: OntologyDiffItem[];
}

export interface OntologyDiffSummary {
  added: number;
  removed: number;
  changed: number;
  unchanged: number;
  total: number;
}

export interface OntologyDiff {
  objects: OntologyDiffItem[];
  attributes: OntologyDiffItem[];
  relations: OntologyDiffItem[];
  summary: OntologyDiffSummary;
  changedItems: OntologyDiffItem[];
}

type UnknownRecord = Record<string, unknown>;

const SEMANTIC_FIELDS = new Set(["className", "name", "relationship"]);

function asRecord(value: unknown): UnknownRecord {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as UnknownRecord) : {};
}

function asRecordList(value: unknown): UnknownRecord[] {
  return Array.isArray(value) ? value.map(asRecord) : [];
}

function stableValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(stableValue);
  if (value && typeof value === "object") {
    return Object.fromEntries(
      Object.entries(value as UnknownRecord)
        .sort(([left], [right]) => left.localeCompare(right))
        .map(([key, nested]) => [key, stableValue(nested)])
    );
  }
  return value;
}

function equalValue(left: unknown, right: unknown): boolean {
  return JSON.stringify(stableValue(left)) === JSON.stringify(stableValue(right));
}

function semanticValue(record: UnknownRecord, key: string): string {
  const value = record[key];
  return typeof value === "string" ? value : String(value ?? "");
}

function keyedRecords(value: unknown, keyField: string): Map<string, UnknownRecord> {
  const map = new Map<string, UnknownRecord>();
  if (Array.isArray(value)) {
    for (const item of asRecordList(value)) {
      const key = semanticValue(item, keyField);
      if (key) map.set(key, item);
    }
    return map;
  }
  for (const [key, item] of Object.entries(asRecord(value))) map.set(key, asRecord(item));
  return map;
}

function changedFields(before: UnknownRecord | null, after: UnknownRecord | null): OntologyDiffField[] {
  const keys = new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})]);
  keys.delete("attrs");
  return [...keys]
    .filter((field) => !SEMANTIC_FIELDS.has(field))
    .sort((left, right) => left.localeCompare(right))
    .map((field) => ({
      field,
      before: before?.[field] ?? null,
      after: after?.[field] ?? null,
      changed: !equalValue(before?.[field], after?.[field]),
    }))
    .filter((field) => field.changed);
}

function createItem(
  key: string,
  label: string,
  before: UnknownRecord | null,
  after: UnknownRecord | null,
  children?: OntologyDiffItem[]
): OntologyDiffItem {
  const fields = changedFields(before, after);
  const childChanged = (children ?? []).some((child) => child.kind !== "unchanged");
  const kind: OntologyDiffKind =
    before === null ? "added" : after === null ? "removed" : fields.length || childChanged ? "changed" : "unchanged";
  return { key, label, kind, before, after, fields, ...(children ? { children } : {}) };
}

function diffKeyed(
  before: Map<string, UnknownRecord>,
  after: Map<string, UnknownRecord>,
  keyField: string,
  childKeyField?: string
): OntologyDiffItem[] {
  return [...new Set([...before.keys(), ...after.keys()])]
    .sort((left, right) => left.localeCompare(right))
    .map((key) => {
      const beforeValue = before.get(key) ?? null;
      const afterValue = after.get(key) ?? null;
      const children = childKeyField
        ? diffKeyed(
            keyedRecords(beforeValue?.attrs, childKeyField),
            keyedRecords(afterValue?.attrs, childKeyField),
            childKeyField
          )
        : undefined;
      return createItem(key, key, beforeValue, afterValue, children);
    });
}

function flattenAttributes(objects: OntologyDiffItem[]): OntologyDiffItem[] {
  return objects.flatMap((object) =>
    (object.children ?? []).map((attribute) => ({
      ...attribute,
      key: `${object.key}.${attribute.key}`,
      label: `${object.label}.${attribute.label}`,
    }))
  );
}

export function diffOntologyRevision(
  before: OntologyRevisionContent,
  after: OntologyRevisionContent
): OntologyDiff {
  const objects = diffKeyed(keyedRecords(before.classDef, "className"), keyedRecords(after.classDef, "className"), "className", "name");
  const attributes = flattenAttributes(objects);
  const relations = diffKeyed(
    keyedRecords(before.relationship_rule ?? before.relations, "relationship"),
    keyedRecords(after.relationship_rule ?? after.relations, "relationship"),
    "relationship"
  );
  const allItems = [...objects, ...attributes, ...relations];
  const summary = allItems.reduce<OntologyDiffSummary>(
    (counts, item) => ({ ...counts, [item.kind]: counts[item.kind] + 1, total: counts.total + 1 }),
    { added: 0, removed: 0, changed: 0, unchanged: 0, total: 0 }
  );
  return {
    objects,
    attributes,
    relations,
    summary,
    changedItems: allItems.filter((item) => item.kind !== "unchanged"),
  };
}

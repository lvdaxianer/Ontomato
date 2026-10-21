<script setup lang="ts">
import { ElTag } from "element-plus";
import { useOntologyText } from "../context";
import type { OntologyDiffItem } from "../ontology-versioning";

const props = defineProps<{
  title: string;
  items: OntologyDiffItem[];
  beforeLabel: string;
  afterLabel: string;
}>();

const { ot } = useOntologyText();

const kindLabels: Record<OntologyDiffItem["kind"], Parameters<typeof ot>[0]> = {
  added: "revisionDiffAdded",
  removed: "revisionDiffRemoved",
  changed: "revisionDiffModified",
  unchanged: "revisionDiffUnchanged",
};

const kindTagType = (kind: OntologyDiffItem["kind"]) =>
  kind === "added" ? "success" : kind === "removed" ? "danger" : kind === "changed" ? "warning" : "info";

const value = (item: OntologyDiffItem, field: "before" | "after") => {
  const source = item[field];
  if (source === null) return "—";
  const content = Object.entries(source)
    .filter(([key]) => key !== "attrs" && key !== "className" && key !== "name" && key !== "relationship")
    .map(([key, entry]) => `${key}: ${typeof entry === "string" ? entry : JSON.stringify(entry)}`)
    .join("\n");
  return content || "—";
};
</script>

<template>
  <section class="diff-section">
    <div class="section-heading">
      <h3>{{ props.title }}</h3>
      <span>{{ props.items.length }}</span>
    </div>
    <template v-if="props.items.length">
      <article v-for="item in props.items" :key="item.key" :class="['diff-item', `diff-${item.kind}`]">
        <div class="diff-item-heading">
          <strong>{{ item.label }}</strong>
          <el-tag size="small" :type="kindTagType(item.kind)">{{ ot(kindLabels[item.kind]) }}</el-tag>
        </div>
        <div class="diff-values">
          <div class="diff-value">
            <span>{{ props.beforeLabel }}</span>
            <pre>{{ value(item, "before") }}</pre>
          </div>
          <div class="diff-arrow" aria-hidden="true">→</div>
          <div class="diff-value">
            <span>{{ props.afterLabel }}</span>
            <pre>{{ value(item, "after") }}</pre>
          </div>
        </div>
        <ul v-if="item.fields.length" class="diff-fields">
          <li v-for="field in item.fields" :key="field.field">
            <strong>{{ field.field }}</strong>
            <span>{{ `${String(field.before ?? "—")} → ${String(field.after ?? "—")}` }}</span>
          </li>
        </ul>
      </article>
    </template>
    <p v-else class="section-empty">—</p>
  </section>
</template>

<style scoped>
.diff-section + .diff-section { margin-top: var(--spacing-xl); }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md); margin-bottom: var(--spacing-sm); color: var(--el-text-color-primary); }
.section-heading h3 { margin: 0; font-size: var(--text-base); }
.section-heading span { color: var(--el-text-color-secondary); font-size: var(--text-sm); }
.diff-item { margin-top: var(--spacing-sm); padding: var(--spacing-md); border: 1px solid var(--el-border-color-light); border-left: 3px solid var(--el-border-color); border-radius: var(--radius-sm); }
.diff-added { border-left-color: var(--el-color-success); }
.diff-removed { border-left-color: var(--el-color-danger); }
.diff-changed { border-left-color: var(--el-color-warning); }
.diff-item-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md); }
.diff-item-heading strong { color: var(--el-text-color-primary); }
.diff-values { display: grid; grid-template-columns: minmax(0, 1fr) 28px minmax(0, 1fr); gap: var(--spacing-sm); margin-top: var(--spacing-sm); }
.diff-value { min-width: 0; padding: var(--spacing-sm); border-radius: var(--radius-sm); background: var(--el-fill-color-lighter); }
.diff-value span { display: block; margin-bottom: 4px; color: var(--el-text-color-secondary); font-size: var(--text-xs); font-weight: var(--font-medium); }
.diff-value pre { min-height: 20px; margin: 0; overflow: auto; color: var(--el-text-color-regular); font-family: var(--font-mono); font-size: var(--text-sm); white-space: pre-wrap; word-break: break-word; }
.diff-arrow { display: flex; align-items: center; justify-content: center; color: var(--el-text-color-secondary); }
.diff-fields { display: flex; flex-wrap: wrap; gap: var(--spacing-xs) var(--spacing-md); margin: var(--spacing-sm) 0 0; padding: 0; list-style: none; color: var(--el-text-color-secondary); font-size: var(--text-xs); }
.diff-fields li { display: flex; gap: 4px; }
.section-empty { color: var(--el-text-color-secondary); }
@container ontology-manager (max-width: 760px) { .diff-values { grid-template-columns: 1fr; } .diff-arrow { display: none; } }
</style>

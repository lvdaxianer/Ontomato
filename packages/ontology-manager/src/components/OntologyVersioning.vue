<script setup lang="ts">
import { computed, defineComponent, h, onMounted, ref, watch, type PropType } from "vue";
import { Check, Download, Eye, FileUp, History, RotateCcw, Upload } from "lucide-vue-next";
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElEmpty,
  ElIcon,
  ElOption,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTag,
} from "element-plus";
import {
  type OntologyImportPreviewResponse,
  type OntologyRevisionList,
  type RestoreOntologyRevisionInput,
} from "../api";
import { useManagerClient, useManagerContext, useOntologyText } from "../context";
import { useManagerFeedback } from "../feedback";
import {
  diffOntologyRevision,
  type OntologyDiff,
  type OntologyDiffItem,
  type OntologyRevisionDocument,
  type OntologyRevisionSummary,
} from "../ontology-versioning";

type DiffFilter = "all" | "changed" | "added" | "removed" | "unchanged";

const { ot } = useOntologyText();
const context = useManagerContext();
const feedback = useManagerFeedback();
const {
  listOntologyRevisions,
  exportOntologyRevision,
  previewOntologyImport,
  applyOntologyImport,
  restoreOntologyRevision,
} = useManagerClient();

const revisions = ref<OntologyRevisionSummary[]>([]);
const activeRevision = ref<number | null>(null);
const baselineRevision = ref<number | null>(null);
const comparisonRevision = ref<number | null>(null);
const baselineDocument = ref<OntologyRevisionDocument | null>(null);
const comparisonDocument = ref<OntologyRevisionDocument | null>(null);
const diffFilter = ref<DiffFilter>("changed");
const loading = ref(false);
const compareLoading = ref(false);
const error = ref("");
const importVisible = ref(false);
const importLoading = ref(false);
const importError = ref("");
const importDocument = ref<OntologyRevisionDocument | null>(null);
const importPreview = ref<OntologyImportPreviewResponse | null>(null);
const resolutions = ref<Record<string, "current" | "incoming"> >({});
const fileInput = ref<HTMLInputElement>();

const diff = computed<OntologyDiff | null>(() => {
  if (!baselineDocument.value || !comparisonDocument.value) return null;
  return diffOntologyRevision(baselineDocument.value.content, comparisonDocument.value.content);
});

const hasUnresolvedConflicts = computed(() =>
  Boolean(importPreview.value?.conflicts.some((conflict) => !resolutions.value[conflict.path]))
);

const importCanApply = computed(
  () => Boolean(importPreview.value?.canApply) && !hasUnresolvedConflicts.value && !importLoading.value
);

const visibleItems = (items: OntologyDiffItem[]) =>
  diffFilter.value === "all" ? items : items.filter((item) => item.kind === diffFilter.value);

const sourceLabel = (source: string) => {
  const labels: Record<string, Parameters<typeof ot>[0]> = {
    manual: "revisionManual",
    import: "revisionSourceImport",
    restore: "revisionRestore",
    migration: "revisionMigration",
    system: "revisionSystem",
  };
  return ot(labels[source] || "revisionSystem");
};

function errorCode(reason: unknown) {
  return typeof reason === "object" && reason !== null && "code" in reason
    ? String((reason as { code?: unknown }).code || "")
    : "";
}

function errorMessage(reason: unknown, fallback: string) {
  const code = errorCode(reason);
  if (code === "ONTOLOGY_REVISION_STALE") return ot("revisionStale");
  return reason instanceof Error && reason.message ? reason.message : fallback;
}

async function loadHistory() {
  loading.value = true;
  error.value = "";
  try {
    const result: OntologyRevisionList = await listOntologyRevisions(100, 0);
    revisions.value = result.items || [];
    activeRevision.value = result.activeRevision ?? result.items?.[0]?.revision ?? null;
    if (!revisions.value.length) {
      baselineRevision.value = null;
      comparisonRevision.value = null;
      return;
    }
    baselineRevision.value =
      baselineRevision.value && revisions.value.some((item) => item.revision === baselineRevision.value)
        ? baselineRevision.value
        : Math.min(...revisions.value.map((item) => item.revision));
    comparisonRevision.value =
      activeRevision.value ?? revisions.value[0]?.revision ?? null;
    await loadComparison();
  } catch (reason) {
    revisions.value = [];
    error.value = errorMessage(reason, ot("revisionLoadFailed"));
  } finally {
    loading.value = false;
  }
}

async function loadComparison() {
  if (baselineRevision.value === null || comparisonRevision.value === null) return;
  compareLoading.value = true;
  try {
    const [baseline, comparison] = await Promise.all([
      exportOntologyRevision(baselineRevision.value),
      exportOntologyRevision(comparisonRevision.value),
    ]);
    baselineDocument.value = baseline;
    comparisonDocument.value = comparison;
  } catch (reason) {
    baselineDocument.value = null;
    comparisonDocument.value = null;
    error.value = errorMessage(reason, ot("revisionLoadFailed"));
  } finally {
    compareLoading.value = false;
  }
}

function revisionLabel(revision: OntologyRevisionSummary) {
  return `${ot("revision", { revision: revision.revision })} · ${sourceLabel(revision.source)}`;
}

function formatValue(value: unknown) {
  if (value === null || value === undefined || value === "") return "—";
  if (typeof value === "string") return value;
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

function formatDate(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(context.locale);
}

function downloadRevision(revisionDocument: OntologyRevisionDocument) {
  const blob = new Blob([JSON.stringify({
    formatVersion: revisionDocument.formatVersion,
    revision: revisionDocument.revision,
    contentHash: revisionDocument.contentHash,
    source: revisionDocument.source,
    actorId: revisionDocument.actorId,
    createdAt: revisionDocument.createdAt,
    content: revisionDocument.content,
  }, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const anchor = globalThis.document.createElement("a");
  anchor.href = url;
  anchor.download = `ontology-revision-${revisionDocument.revision}.json`;
  anchor.click();
  URL.revokeObjectURL(url);
}

function resetImport() {
  importDocument.value = null;
  importPreview.value = null;
  resolutions.value = {};
  importError.value = "";
  if (fileInput.value) fileInput.value.value = "";
}

function openImport() {
  resetImport();
  importVisible.value = true;
}

function isRevisionDocument(value: unknown): value is OntologyRevisionDocument {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.revision === "number" &&
    typeof candidate.contentHash === "string" &&
    candidate.content !== null &&
    typeof candidate.content === "object" &&
    !Array.isArray(candidate.content)
  );
}

async function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  importError.value = "";
  importPreview.value = null;
  try {
    const parsed: unknown = JSON.parse(await file.text());
    if (!isRevisionDocument(parsed)) throw new Error(ot("revisionImportInvalid"));
    importDocument.value = parsed;
  } catch (reason) {
    importDocument.value = null;
    importError.value = reason instanceof Error ? reason.message : ot("revisionImportInvalid");
  }
}

async function previewImport() {
  if (!importDocument.value) return;
  importLoading.value = true;
  importError.value = "";
  try {
    importPreview.value = await previewOntologyImport({
      content: importDocument.value.content,
      baseRevision: importDocument.value.revision,
      baseHash: importDocument.value.contentHash,
    });
    resolutions.value = {};
  } catch (reason) {
    importError.value = errorMessage(reason, ot("revisionImportFailed"));
  } finally {
    importLoading.value = false;
  }
}

async function applyImport() {
  if (!importDocument.value || !importCanApply.value) return;
  importLoading.value = true;
  importError.value = "";
  try {
    await applyOntologyImport({
      content: importDocument.value.content,
      baseRevision: importDocument.value.revision,
      baseHash: importDocument.value.contentHash,
      resolutions: resolutions.value,
    });
    feedback.message.success(ot("revisionImportApplied"));
    importVisible.value = false;
    resetImport();
    await loadHistory();
  } catch (reason) {
    importError.value = errorMessage(reason, ot("revisionImportFailed"));
  } finally {
    importLoading.value = false;
  }
}

async function restoreRevision(revision: OntologyRevisionSummary) {
  try {
    await feedback.confirm(
      ot("revisionRestoreConfirm", { revision: revision.revision }),
      ot("revisionRestoreAction")
    );
  } catch {
    return;
  }
  const input: RestoreOntologyRevisionInput = {
    targetRevision: revision.revision,
    expectedCurrentRevision: activeRevision.value || revision.revision,
    expectedCurrentHash:
      revisions.value.find((item) => item.revision === activeRevision.value)?.contentHash || revision.contentHash,
  };
  try {
    await restoreOntologyRevision(input);
    feedback.message.success(ot("revisionRestored"));
    await loadHistory();
  } catch (reason) {
    error.value = errorMessage(reason, ot("revisionLoadFailed"));
  }
}

const DiffSection = defineComponent({
  name: "DiffSection",
  props: {
    title: { type: String, required: true },
    items: { type: Array as PropType<OntologyDiffItem[]>, required: true },
    beforeLabel: { type: String, required: true },
    afterLabel: { type: String, required: true },
  },
  setup(props) {
    const value = (item: OntologyDiffItem, field: "before" | "after") => {
      const source = item[field];
      if (source === null) return "—";
      const content = Object.entries(source)
        .filter(([key]) => key !== "attrs" && key !== "className" && key !== "name" && key !== "relationship")
        .map(([key, entry]) => `${key}: ${typeof entry === "string" ? entry : JSON.stringify(entry)}`)
        .join("\n");
      return content || "—";
    };
    return () =>
      h("section", { class: "diff-section" }, [
        h("div", { class: "section-heading" }, [h("h3", props.title), h("span", `${props.items.length}`)]),
        props.items.length
          ? props.items.map((item) =>
              h("article", { key: item.key, class: ["diff-item", `diff-${item.kind}`] }, [
                h("div", { class: "diff-item-heading" }, [
                  h("strong", item.label),
                  h(ElTag, { size: "small", type: item.kind === "added" ? "success" : item.kind === "removed" ? "danger" : item.kind === "changed" ? "warning" : "info" }, () => item.kind),
                ]),
                h("div", { class: "diff-values" }, [
                  h("div", { class: "diff-value" }, [h("span", props.beforeLabel), h("pre", value(item, "before"))]),
                  h("div", { class: "diff-arrow" }, "→"),
                  h("div", { class: "diff-value" }, [h("span", props.afterLabel), h("pre", value(item, "after"))]),
                ]),
                item.fields.length
                  ? h("ul", { class: "diff-fields" }, item.fields.map((field) => h("li", { key: field.field }, [h("strong", field.field), h("span", `${String(field.before ?? "—")} → ${String(field.after ?? "—")}`)])))
                  : null,
              ])
            )
          : h("p", { class: "section-empty" }, "—"),
      ]);
  },
});

function setResolution(path: string, value: "current" | "incoming") {
  resolutions.value = { ...resolutions.value, [path]: value };
}

watch([baselineRevision, comparisonRevision], () => {
  if (!loading.value) void loadComparison();
});

onMounted(() => void loadHistory());
</script>

<template>
  <section class="revision-surface" :aria-busy="loading || compareLoading || importLoading">
    <header class="revision-header">
      <div>
        <div class="revision-eyebrow"><History :size="16" /> {{ ot("activeRevision") }}</div>
        <h1>{{ ot("ontologyVersionHistory") }}</h1>
        <p>{{ ot("ontologyVersionHistoryDesc") }}</p>
      </div>
      <div class="revision-actions">
        <el-button :icon="Upload" @click="openImport">{{ ot("revisionImport") }}</el-button>
        <el-button v-if="comparisonDocument" :icon="Download" @click="downloadRevision(comparisonDocument)">
          {{ ot("revisionExport") }}
        </el-button>
      </div>
    </header>

    <el-alert v-if="error" type="error" :closable="false" show-icon class="revision-alert">
      <template #title>
        <span>{{ error }}</span>
        <el-button link type="danger" @click="loadHistory">{{ ot("revisionRetry") }}</el-button>
      </template>
    </el-alert>

    <div v-if="loading" class="revision-state">
      <el-icon class="is-loading"><History /></el-icon>
      <span>{{ ot("revisionLoading") }}</span>
    </div>
    <el-empty v-else-if="!revisions.length" :description="ot('revisionNoHistory')">
      <template #default><p class="empty-description">{{ ot("revisionNoHistoryDesc") }}</p></template>
    </el-empty>

    <div v-else class="revision-layout">
      <aside class="revision-list" aria-label="Revision history">
        <div class="panel-heading">
          <strong>{{ ot("ontologyVersionHistory") }}</strong>
          <el-tag size="small" type="success">{{ ot("revision", { revision: activeRevision || 0 }) }}</el-tag>
        </div>
        <button
          v-for="revision in revisions"
          :key="revision.revision"
          :class="['revision-row', { active: revision.revision === activeRevision }]"
          @click="comparisonRevision = revision.revision"
        >
          <span class="revision-row-main">
            <strong>{{ revisionLabel(revision) }}</strong>
            <small>{{ formatDate(revision.createdAt) }}</small>
          </span>
          <Check v-if="revision.revision === activeRevision" :size="16" />
        </button>
      </aside>

      <main class="revision-main">
        <section class="compare-toolbar">
          <div>
            <h2>{{ ot("revisionCompareTitle") }}</h2>
            <p>{{ ot("revisionFirst") }} → {{ ot("revisionCurrent") }}</p>
          </div>
          <div class="compare-selects">
            <el-select v-model="baselineRevision" :aria-label="ot('revisionFirst')" class="revision-select">
              <el-option v-for="item in revisions" :key="`base-${item.revision}`" :label="revisionLabel(item)" :value="item.revision" />
            </el-select>
            <span aria-hidden="true">→</span>
            <el-select v-model="comparisonRevision" :aria-label="ot('revisionCurrent')" class="revision-select">
              <el-option v-for="item in revisions" :key="`compare-${item.revision}`" :label="revisionLabel(item)" :value="item.revision" />
            </el-select>
          </div>
        </section>

        <section v-if="diff" class="diff-panel">
          <div class="summary-grid">
            <div class="summary-card"><strong>{{ diff.summary.added }}</strong><span>{{ ot("revisionDiffAdded") }}</span></div>
            <div class="summary-card"><strong>{{ diff.summary.removed }}</strong><span>{{ ot("revisionDiffRemoved") }}</span></div>
            <div class="summary-card"><strong>{{ diff.summary.changed }}</strong><span>{{ ot("revisionDiffChanged") }}</span></div>
            <div class="summary-card summary-card-muted"><strong>{{ diff.summary.unchanged }}</strong><span>{{ ot("revisionDiffUnchanged") }}</span></div>
          </div>
          <div class="filter-row" role="group" :aria-label="ot('revisionCompareTitle')">
            <el-radio-group v-model="diffFilter" size="small">
              <el-radio-button value="all">{{ ot("revisionDiffAll") }}</el-radio-button>
              <el-radio-button value="changed">{{ ot("revisionDiffChanged") }}</el-radio-button>
              <el-radio-button value="added">{{ ot("revisionDiffAdded") }}</el-radio-button>
              <el-radio-button value="removed">{{ ot("revisionDiffRemoved") }}</el-radio-button>
              <el-radio-button value="unchanged">{{ ot("revisionDiffUnchanged") }}</el-radio-button>
            </el-radio-group>
          </div>

          <DiffSection :title="ot('revisionObjects')" :items="visibleItems(diff.objects)" :before-label="ot('revisionBefore')" :after-label="ot('revisionAfter')" />
          <DiffSection :title="ot('revisionAttributes')" :items="visibleItems(diff.attributes)" :before-label="ot('revisionBefore')" :after-label="ot('revisionAfter')" />
          <DiffSection :title="ot('revisionRelations')" :items="visibleItems(diff.relations)" :before-label="ot('revisionBefore')" :after-label="ot('revisionAfter')" />
        </section>
        <div v-else class="revision-state"><Eye :size="20" /> {{ ot("revisionLoading") }}</div>

        <section class="active-revision-card" v-if="comparisonDocument">
          <div>
            <span class="card-label">{{ comparisonDocument.revision === activeRevision ? ot("activeRevision") : ot("revisionCompare") }}</span>
            <strong>{{ ot("revision", { revision: comparisonDocument.revision }) }}</strong>
            <small>{{ sourceLabel(comparisonDocument.source) }} · {{ formatDate(comparisonDocument.createdAt) }}</small>
          </div>
          <el-button
            v-if="comparisonDocument.revision !== activeRevision"
            type="warning"
            plain
            :icon="RotateCcw"
            @click="restoreRevision(comparisonDocument)"
          >
            {{ ot("revisionRestoreAction") }}
          </el-button>
        </section>
      </main>
    </div>

    <el-dialog v-model="importVisible" :title="ot('revisionImport')" width="720px" destroy-on-close>
      <div class="import-flow">
        <p class="import-hint"><FileUp :size="18" /> {{ ot("revisionImportHint") }}</p>
        <input ref="fileInput" class="file-input" type="file" accept="application/json,.json" @change="handleImportFile" />
        <el-alert v-if="importError" type="error" :closable="false" show-icon>{{ importError }}</el-alert>
        <el-descriptions v-if="importDocument" :column="2" border>
          <el-descriptions-item :label="ot('revision')">{{ importDocument.revision }}</el-descriptions-item>
          <el-descriptions-item :label="ot('revisionSource')">{{ sourceLabel(importDocument.source) }}</el-descriptions-item>
          <el-descriptions-item :label="ot('revisionCreatedAt')">{{ formatDate(importDocument.createdAt) }}</el-descriptions-item>
          <el-descriptions-item :label="ot('revisionActor')">{{ importDocument.actorId || "—" }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="importPreview" class="import-preview">
          <el-alert
            :type="importPreview.conflicts.length ? 'warning' : 'success'"
            :closable="false"
            show-icon
          >
            {{ importPreview.conflicts.length
              ? ot("revisionImportConflicts", { count: importPreview.conflicts.length })
              : ot("revisionImportNoConflicts") }}
          </el-alert>
          <div v-for="conflict in importPreview.conflicts" :key="conflict.path" class="conflict-row">
            <div class="conflict-path">{{ conflict.path }}</div>
            <div class="conflict-values">
              <label><span>{{ ot("revisionConflictCurrent") }}</span><code>{{ formatValue(conflict.currentValue) }}</code><el-radio :model-value="resolutions[conflict.path]" value="current" @change="setResolution(conflict.path, 'current')">{{ ot("revisionUseCurrent") }}</el-radio></label>
              <label><span>{{ ot("revisionConflictIncoming") }}</span><code>{{ formatValue(conflict.incomingValue) }}</code><el-radio :model-value="resolutions[conflict.path]" value="incoming" @change="setResolution(conflict.path, 'incoming')">{{ ot("revisionUseIncoming") }}</el-radio></label>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">{{ ot("cancel") }}</el-button>
        <el-button type="primary" :loading="importLoading" :disabled="!importDocument" :icon="Eye" @click="previewImport">{{ ot("revisionImportPreview") }}</el-button>
        <el-button type="success" :loading="importLoading" :disabled="!importCanApply" :icon="Check" @click="applyImport">{{ ot("revisionImportApply") }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.revision-surface { display: flex; min-height: 0; flex: 1; flex-direction: column; overflow: auto; padding: var(--spacing-2xl); background: var(--el-bg-color); }
.revision-header, .compare-toolbar, .panel-heading, .active-revision-card, .diff-item-heading, .section-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md); }
.revision-header { margin-bottom: var(--spacing-xl); }
.revision-header h1, .compare-toolbar h2 { margin: 4px 0; color: var(--el-text-color-primary); }
.revision-header p, .compare-toolbar p { margin: 0; color: var(--el-text-color-secondary); }
.revision-eyebrow, .import-hint { display: flex; align-items: center; gap: var(--spacing-xs); color: var(--el-color-primary); font-size: var(--text-sm); font-weight: var(--font-medium); }
.revision-actions { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.revision-alert { margin-bottom: var(--spacing-md); }
.revision-layout { display: grid; min-height: 0; flex: 1; grid-template-columns: 280px minmax(0, 1fr); gap: var(--spacing-xl); }
.revision-list, .revision-main, .diff-panel, .active-revision-card { border: 1px solid var(--el-border-color); border-radius: var(--radius-md); background: var(--el-bg-color); }
.revision-list { align-self: start; overflow: hidden; }
.panel-heading { padding: var(--spacing-md); border-bottom: 1px solid var(--el-border-color-light); }
.revision-row { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: var(--spacing-sm); padding: var(--spacing-md); border: 0; border-bottom: 1px solid var(--el-border-color-lighter); color: var(--el-text-color-regular); background: transparent; cursor: pointer; text-align: left; }
.revision-row:hover, .revision-row.active { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.revision-row-main { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.revision-row-main strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.revision-row-main small { color: var(--el-text-color-secondary); }
.revision-main { min-width: 0; padding: var(--spacing-xl); }
.compare-toolbar { align-items: flex-end; margin-bottom: var(--spacing-xl); }
.compare-selects { display: flex; align-items: center; gap: var(--spacing-sm); }
.revision-select { min-width: 210px; }
.diff-panel { padding: var(--spacing-lg); border: 0; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(100px, 1fr)); gap: var(--spacing-sm); }
.summary-card { display: flex; flex-direction: column; gap: 2px; padding: var(--spacing-md); border-radius: var(--radius-sm); color: var(--el-color-success); background: var(--el-color-success-light-9); }
.summary-card:nth-child(2) { color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.summary-card:nth-child(3) { color: var(--el-color-warning); background: var(--el-color-warning-light-9); }
.summary-card-muted { color: var(--el-text-color-secondary); background: var(--el-fill-color-light); }
.summary-card strong { font-size: 24px; }
.summary-card span { font-size: var(--text-sm); }
.filter-row { display: flex; justify-content: flex-end; margin: var(--spacing-lg) 0; }
.diff-section + .diff-section { margin-top: var(--spacing-xl); }
.section-heading { margin-bottom: var(--spacing-sm); color: var(--el-text-color-primary); }
.section-heading h3 { margin: 0; font-size: var(--text-md); }
.section-heading span { color: var(--el-text-color-secondary); font-size: var(--text-sm); }
.diff-item { margin-top: var(--spacing-sm); padding: var(--spacing-md); border: 1px solid var(--el-border-color-light); border-left: 3px solid var(--el-border-color); border-radius: var(--radius-sm); }
.diff-added { border-left-color: var(--el-color-success); }
.diff-removed { border-left-color: var(--el-color-danger); }
.diff-changed { border-left-color: var(--el-color-warning); }
.diff-item-heading strong { color: var(--el-text-color-primary); }
.diff-values { display: grid; grid-template-columns: minmax(0, 1fr) 28px minmax(0, 1fr); gap: var(--spacing-sm); margin-top: var(--spacing-sm); }
.diff-value { min-width: 0; padding: var(--spacing-sm); border-radius: var(--radius-sm); background: var(--el-fill-color-lighter); }
.diff-value span { display: block; margin-bottom: 4px; color: var(--el-text-color-secondary); font-size: var(--text-xs); font-weight: var(--font-medium); }
.diff-value pre { min-height: 20px; margin: 0; overflow: auto; color: var(--el-text-color-regular); font: inherit; white-space: pre-wrap; word-break: break-word; }
.diff-arrow { display: flex; align-items: center; justify-content: center; color: var(--el-text-color-secondary); }
.diff-fields { display: flex; flex-wrap: wrap; gap: var(--spacing-xs) var(--spacing-md); margin: var(--spacing-sm) 0 0; padding: 0; list-style: none; color: var(--el-text-color-secondary); font-size: var(--text-xs); }
.diff-fields li { display: flex; gap: 4px; }
.section-empty, .empty-description { color: var(--el-text-color-secondary); }
.active-revision-card { display: flex; align-items: center; justify-content: space-between; margin-top: var(--spacing-xl); padding: var(--spacing-md); }
.active-revision-card > div { display: flex; flex-direction: column; gap: 4px; }
.card-label { color: var(--el-text-color-secondary); font-size: var(--text-xs); }
.active-revision-card small { color: var(--el-text-color-secondary); }
.revision-state { display: flex; min-height: 180px; align-items: center; justify-content: center; gap: var(--spacing-sm); color: var(--el-text-color-secondary); }
.import-flow { display: flex; flex-direction: column; gap: var(--spacing-md); }
.file-input { width: 100%; padding: var(--spacing-md); border: 1px dashed var(--el-border-color); border-radius: var(--radius-sm); background: var(--el-fill-color-lighter); }
.import-hint { margin: 0; color: var(--el-text-color-regular); }
.import-preview { display: flex; flex-direction: column; gap: var(--spacing-md); }
.conflict-row { padding: var(--spacing-md); border: 1px solid var(--el-color-warning-light-5); border-radius: var(--radius-sm); background: var(--el-color-warning-light-9); }
.conflict-path { margin-bottom: var(--spacing-sm); color: var(--el-text-color-primary); font-family: var(--font-mono); font-size: var(--text-sm); font-weight: var(--font-medium); }
.conflict-values { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--spacing-md); }
.conflict-values label { display: grid; gap: var(--spacing-xs); padding: var(--spacing-sm); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-sm); background: var(--el-bg-color); }
.conflict-values label > span { color: var(--el-text-color-secondary); font-size: var(--text-xs); }
.conflict-values code { overflow: auto; white-space: pre-wrap; word-break: break-word; }
@container ontology-manager (max-width: 1000px) { .revision-layout { grid-template-columns: 220px minmax(0, 1fr); } .revision-select { min-width: 160px; } }
@container ontology-manager (max-width: 760px) { .revision-surface { padding: var(--spacing-md); } .revision-header, .compare-toolbar { align-items: stretch; flex-direction: column; } .revision-layout { display: flex; flex-direction: column; } .revision-list { max-height: 260px; overflow: auto; } .compare-selects { flex-wrap: wrap; } .revision-select { min-width: min(100%, 240px); flex: 1; } .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .diff-values, .conflict-values { grid-template-columns: 1fr; } .diff-arrow { display: none; } .active-revision-card { align-items: flex-start; flex-direction: column; } }
</style>

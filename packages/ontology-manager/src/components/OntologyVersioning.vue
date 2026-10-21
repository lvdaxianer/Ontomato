<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { ArrowRight, Check, CircleCheck, Download, Equal, Eye, FileUp, GitCompare, History, Minus, PenLine, Plus, RotateCcw, Upload } from "lucide-vue-next";
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
import DiffSection from "./OntologyDiffSection.vue";

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

const totalVisible = computed(() => {
  if (!diff.value) return 0;
  return (
    visibleItems(diff.value.objects).length +
    visibleItems(diff.value.attributes).length +
    visibleItems(diff.value.relations).length
  );
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
        <ol class="revision-timeline">
          <li
            v-for="revision in revisions"
            :key="revision.revision"
            :class="[
              'revision-item',
              { active: revision.revision === activeRevision, selected: revision.revision === comparisonRevision },
            ]"
          >
            <button class="revision-row" @click="comparisonRevision = revision.revision">
              <span class="revision-row-main">
                <strong>{{ revisionLabel(revision) }}</strong>
                <small>{{ formatDate(revision.createdAt) }}</small>
              </span>
              <span class="revision-row-marker">
                <GitCompare v-if="revision.revision === comparisonRevision && revision.revision !== activeRevision" :size="16" />
                <Check v-else-if="revision.revision === activeRevision" :size="16" />
              </span>
            </button>
          </li>
        </ol>
      </aside>

      <main class="revision-main">
        <section class="compare-toolbar">
          <div class="compare-heading">
            <h2>{{ ot("revisionCompareTitle") }}</h2>
          </div>
          <div class="compare-selects">
            <label class="compare-field">
              <span>{{ ot("revisionBaseline") }}</span>
              <el-select v-model="baselineRevision" :aria-label="ot('revisionBaseline')" class="revision-select">
                <el-option v-for="item in revisions" :key="`base-${item.revision}`" :label="revisionLabel(item)" :value="item.revision" />
              </el-select>
            </label>
            <span class="compare-arrow" aria-hidden="true"><ArrowRight :size="18" /></span>
            <label class="compare-field">
              <span>{{ ot("revisionCompareTarget") }}</span>
              <el-select v-model="comparisonRevision" :aria-label="ot('revisionCompareTarget')" class="revision-select">
                <el-option v-for="item in revisions" :key="`compare-${item.revision}`" :label="revisionLabel(item)" :value="item.revision" />
              </el-select>
            </label>
          </div>
        </section>

        <section v-if="diff" class="diff-panel">
          <div class="summary-grid">
            <div class="summary-card summary-added">
              <span class="summary-icon"><Plus :size="18" /></span>
              <div class="summary-text"><strong>{{ diff.summary.added }}</strong><span>{{ ot("revisionDiffAdded") }}</span></div>
            </div>
            <div class="summary-card summary-removed">
              <span class="summary-icon"><Minus :size="18" /></span>
              <div class="summary-text"><strong>{{ diff.summary.removed }}</strong><span>{{ ot("revisionDiffRemoved") }}</span></div>
            </div>
            <div class="summary-card summary-changed">
              <span class="summary-icon"><PenLine :size="18" /></span>
              <div class="summary-text"><strong>{{ diff.summary.changed }}</strong><span>{{ ot("revisionDiffModified") }}</span></div>
            </div>
            <div class="summary-card summary-unchanged">
              <span class="summary-icon"><Equal :size="18" /></span>
              <div class="summary-text"><strong>{{ diff.summary.unchanged }}</strong><span>{{ ot("revisionDiffUnchanged") }}</span></div>
            </div>
          </div>
          <div class="filter-row">
            <span v-if="totalVisible" class="filter-count">{{ ot("revisionDiffCount", { count: totalVisible }) }}</span>
            <el-radio-group v-model="diffFilter" size="small">
              <el-radio-button value="all">{{ ot("revisionDiffAll") }}</el-radio-button>
              <el-radio-button value="changed">{{ ot("revisionDiffChanged") }}</el-radio-button>
              <el-radio-button value="added">{{ ot("revisionDiffAdded") }}</el-radio-button>
              <el-radio-button value="removed">{{ ot("revisionDiffRemoved") }}</el-radio-button>
              <el-radio-button value="unchanged">{{ ot("revisionDiffUnchanged") }}</el-radio-button>
            </el-radio-group>
          </div>

          <div v-if="totalVisible === 0" class="diff-empty">
            <span class="diff-empty-icon"><CircleCheck :size="30" /></span>
            <p>{{ baselineRevision === comparisonRevision ? ot("revisionDiffIdentical") : ot("revisionDiffEmpty") }}</p>
          </div>
          <template v-else>
            <DiffSection :title="ot('revisionObjects')" :items="visibleItems(diff.objects)" :before-label="ot('revisionBefore')" :after-label="ot('revisionAfter')" />
            <DiffSection :title="ot('revisionAttributes')" :items="visibleItems(diff.attributes)" :before-label="ot('revisionBefore')" :after-label="ot('revisionAfter')" />
            <DiffSection :title="ot('revisionRelations')" :items="visibleItems(diff.relations)" :before-label="ot('revisionBefore')" :after-label="ot('revisionAfter')" />
          </template>
        </section>
        <div v-else class="revision-state"><Eye :size="20" /> {{ ot("revisionLoading") }}</div>

        <section v-if="comparisonDocument" class="active-revision-card">
          <div class="active-revision-meta">
            <span class="active-revision-badge">
              <History :size="14" />
              {{ comparisonDocument.revision === activeRevision ? ot("activeRevision") : ot("revisionCompare") }}
            </span>
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
.revision-header, .panel-heading, .active-revision-card { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md); }
.revision-header { margin-bottom: var(--spacing-xl); }
.revision-header h1 { margin: 4px 0; color: var(--el-text-color-primary); }
.revision-header p { margin: 0; color: var(--el-text-color-secondary); }
.revision-eyebrow, .import-hint { display: flex; align-items: center; gap: var(--spacing-xs); color: var(--el-color-primary); font-size: var(--text-sm); font-weight: var(--font-medium); }
.revision-actions { display: flex; flex-wrap: wrap; gap: var(--spacing-sm); }
.revision-alert { margin-bottom: var(--spacing-md); }
.revision-layout { display: grid; min-height: 0; flex: 1; grid-template-columns: 300px minmax(0, 1fr); gap: var(--spacing-xl); }
.revision-list, .revision-main, .diff-panel, .active-revision-card { border: 1px solid var(--el-border-color); border-radius: var(--radius-lg); background: var(--el-bg-color); }
.revision-list { align-self: start; overflow: hidden; }
.panel-heading { padding: var(--spacing-md) var(--spacing-lg); border-bottom: 1px solid var(--el-border-color-light); }
.panel-heading strong { color: var(--el-text-color-primary); font-size: var(--text-sm); }
.revision-timeline { position: relative; margin: 0; padding: var(--spacing-sm) 0; list-style: none; }
.revision-timeline::before { content: ""; position: absolute; top: var(--spacing-lg); bottom: var(--spacing-lg); left: 23px; width: 2px; border-radius: var(--radius-full); background: var(--el-border-color-light); }
.revision-item { position: relative; }
.revision-row { position: relative; display: flex; width: calc(100% - var(--spacing-md)); align-items: center; justify-content: space-between; gap: var(--spacing-sm); margin: 2px var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md) var(--spacing-sm) 42px; border: 0; border-radius: var(--radius-md); color: var(--el-text-color-regular); background: transparent; cursor: pointer; text-align: left; transition: background var(--transition-fast), color var(--transition-fast); }
.revision-row::before { content: ""; position: absolute; top: 50%; left: 18px; width: 10px; height: 10px; border: 2px solid var(--el-border-color-dark); border-radius: var(--radius-full); background: var(--el-bg-color); transform: translateY(-50%); transition: border-color var(--transition-fast), box-shadow var(--transition-fast); }
.revision-row:hover { color: var(--el-color-primary); background: var(--el-fill-color-light); }
.revision-item.active .revision-row { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.revision-item.active .revision-row::before { border-color: var(--el-color-primary); background: var(--el-color-primary); box-shadow: 0 0 0 3px var(--el-color-primary-light-8); }
.revision-item.selected:not(.active) .revision-row { color: var(--el-color-primary); }
.revision-item.selected:not(.active) .revision-row::before { border-color: var(--el-color-primary); }
.revision-row-main { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.revision-row-main strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: var(--text-sm); }
.revision-row-main small { color: var(--el-text-color-secondary); font-size: var(--text-xs); }
.revision-item.active .revision-row-main small, .revision-item.selected .revision-row-main small { color: inherit; opacity: 0.75; }
.revision-row-marker { display: flex; width: 20px; align-items: center; justify-content: center; color: var(--el-color-primary); flex-shrink: 0; }
.revision-main { display: flex; min-width: 0; flex-direction: column; padding: var(--spacing-xl); }
.compare-toolbar { display: flex; flex-direction: column; gap: var(--spacing-md); margin-bottom: var(--spacing-lg); padding-bottom: var(--spacing-lg); border-bottom: 1px solid var(--el-border-color-light); }
.compare-heading h2 { margin: 0; color: var(--el-text-color-primary); }
.compare-selects { display: flex; align-items: flex-end; gap: var(--spacing-md); }
.compare-field { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: var(--spacing-xs); color: var(--el-text-color-secondary); font-size: var(--text-xs); font-weight: var(--font-medium); }
.revision-select { width: 100%; }
.compare-arrow { display: flex; width: 28px; height: 28px; margin-bottom: 5px; align-items: center; justify-content: center; border-radius: var(--radius-full); color: var(--el-color-primary); background: var(--el-color-primary-light-9); flex-shrink: 0; }
.diff-panel { padding: 0; border: 0; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: var(--spacing-md); }
.summary-card { display: flex; align-items: center; gap: var(--spacing-md); padding: var(--spacing-md) var(--spacing-lg); border: 1px solid; border-radius: var(--radius-md); transition: transform var(--transition-fast), box-shadow var(--transition-fast); }
.summary-card:hover { transform: translateY(-1px); box-shadow: var(--el-box-shadow-lighter); }
.summary-icon { display: flex; width: 34px; height: 34px; align-items: center; justify-content: center; border-radius: var(--radius-full); background: color-mix(in srgb, currentColor 14%, transparent); flex-shrink: 0; }
.summary-text { display: flex; flex-direction: column; line-height: var(--leading-tight); }
.summary-text strong { font-size: var(--text-xl); }
.summary-text span { color: var(--el-text-color-secondary); font-size: var(--text-xs); }
.summary-added { color: var(--el-color-success); border-color: color-mix(in srgb, var(--el-color-success) 28%, var(--el-bg-color)); background: color-mix(in srgb, var(--el-color-success) 8%, var(--el-bg-color)); }
.summary-removed { color: var(--el-color-danger); border-color: color-mix(in srgb, var(--el-color-danger) 28%, var(--el-bg-color)); background: color-mix(in srgb, var(--el-color-danger) 8%, var(--el-bg-color)); }
.summary-changed { color: var(--el-color-warning); border-color: color-mix(in srgb, var(--el-color-warning) 28%, var(--el-bg-color)); background: color-mix(in srgb, var(--el-color-warning) 8%, var(--el-bg-color)); }
.summary-unchanged { color: var(--el-text-color-secondary); border-color: var(--el-border-color-light); background: var(--el-fill-color-light); }
.filter-row { display: flex; align-items: center; justify-content: space-between; gap: var(--spacing-md); margin: var(--spacing-lg) 0; }
.filter-count { color: var(--el-text-color-secondary); font-size: var(--text-sm); }
.diff-empty { display: flex; flex-direction: column; align-items: center; gap: var(--spacing-md); padding: var(--spacing-3xl) var(--spacing-lg); color: var(--el-text-color-secondary); text-align: center; }
.diff-empty-icon { display: flex; width: 56px; height: 56px; align-items: center; justify-content: center; border-radius: var(--radius-full); color: var(--el-color-success); background: var(--el-color-success-light-9); }
.diff-empty p { margin: 0; }
.section-empty, .empty-description { color: var(--el-text-color-secondary); }
.active-revision-card { margin-top: var(--spacing-lg); padding: var(--spacing-md) var(--spacing-lg); background: var(--el-fill-color-light); }
.active-revision-meta { display: flex; min-width: 0; align-items: center; gap: var(--spacing-md); }
.active-revision-badge { display: inline-flex; align-items: center; gap: 4px; padding: 2px var(--spacing-sm); border-radius: var(--radius-full); color: var(--el-color-primary); background: var(--el-color-primary-light-9); font-size: var(--text-xs); font-weight: var(--font-medium); flex-shrink: 0; }
.active-revision-meta strong { color: var(--el-text-color-primary); font-size: var(--text-lg); }
.active-revision-meta small { overflow: hidden; color: var(--el-text-color-secondary); text-overflow: ellipsis; white-space: nowrap; }
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
@container ontology-manager (max-width: 1000px) { .revision-layout { grid-template-columns: 240px minmax(0, 1fr); } }
@container ontology-manager (max-width: 760px) { .revision-surface { padding: var(--spacing-md); } .revision-header { align-items: stretch; flex-direction: column; } .revision-layout { display: flex; flex-direction: column; } .revision-list { max-height: 280px; overflow: auto; } .revision-main { padding: var(--spacing-lg); } .compare-selects { align-items: stretch; flex-direction: column; } .compare-arrow { align-self: center; margin-bottom: 0; transform: rotate(90deg); } .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .conflict-values { grid-template-columns: 1fr; } .active-revision-card { align-items: flex-start; flex-direction: column; } }
</style>

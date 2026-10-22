<script setup lang="ts">
import type { Component } from "vue";
import type { LoopSubagentTrace } from "@ontomato/contracts/analysis-task";
import type {
  AnalysisActivity,
  AnalysisEvidenceQuestion,
  DeepAnalysisTaskPayload,
} from "@ontomato/contracts/analysis-presentation";

import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import {
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  CircleSlash,
  CircleX,
  Clock,
  Loader2,
} from "lucide-vue-next";
import { analysisTaskApi } from "../api";
import {
  loopActivityDetailFields,
  loopActivityLabel,
  loopActivitySummary,
  loopQuestionStatusText,
  type LoopDetailField,
  type LoopStepKind,
} from "../utils/loop-activity";
import { buildDimensionTree, dimensionStageStates, dimensionTone } from "../utils/dimension-view";
import { processIssueCounts, type ProcessTone } from "../utils/process-view";
import type { DeepAnalysisQuestion } from "../../../types/chat";
import AgentMessageTrace from "./AgentMessageTrace.vue";
import QuestionExecution, { type QuestionExecutionScope } from "./QuestionExecution.vue";

/**
 * One turn's work process for both execution forms. The run state is the only authority for the
 * turn's own status; expansion choices live here so they survive streaming updates and completion
 * of the same turn.
 */
const props = defineProps<{
  payload: DeepAnalysisTaskPayload;
  /** Where this turn's deferred question facts are read. */
  executionScope?: QuestionExecutionScope;
  /** Task owning the dispatch worker traces; traces are only readable for a task. */
  taskId?: string;
}>();

const { t } = useI18n();

const TONE_ICONS: Record<ProcessTone, Component> = {
  running: Loader2,
  completed: CheckCircle2,
  failed: CircleX,
  cancelled: CircleSlash,
  pending: Clock,
};

const TONE_TEXT_KEYS: Record<ProcessTone, string> = {
  running: "common.inProgress",
  completed: "analysis.completed",
  failed: "common.failed",
  cancelled: "common.stopped",
  pending: "analysis.processPending",
};

const status = computed<ProcessTone>(() => props.payload.runState.status);
const isLoop = computed(() => props.payload.executionMode === "loop");

// The running and failed turns open by default; a finished turn keeps a compact overview. Any
// explicit choice, including opening a step inside, pins the process against later updates.
const processChoice = ref<boolean | null>(null);
const expanded = computed(
  () => processChoice.value ?? (status.value === "running" || status.value === "failed")
);
function toggleProcess() {
  processChoice.value = !expanded.value;
}
function pinProcess() {
  if (processChoice.value === null) processChoice.value = true;
}

const itemChoices = ref<Record<string, boolean>>({});
function isItemExpanded(key: string, byDefault = false): boolean {
  return itemChoices.value[key] ?? byDefault;
}
function toggleItem(key: string, byDefault = false) {
  pinProcess();
  itemChoices.value = { ...itemChoices.value, [key]: !isItemExpanded(key, byDefault) };
}

const issues = computed(() => processIssueCounts(props.payload.activities));

// ---- Dimension orchestration: plan dimensions -> questions -> evidence ----

const stageNames = [
  t("analysis.frameworkGeneration"),
  t("analysis.metricCollection"),
  t("analysis.dataAnalysis"),
  t("analysis.reportGeneration"),
];
const STAGE_TONES = {
  done: "completed",
  active: "running",
  pending: "pending",
  stopped: "cancelled",
  failed: "failed",
} as const satisfies Record<string, ProcessTone>;

const stages = computed(() =>
  isLoop.value
    ? []
    : dimensionStageStates(props.payload).map((state, index) => ({
        name: stageNames[index],
        tone: STAGE_TONES[state] as ProcessTone,
      }))
);

interface QuestionRow {
  key: string;
  text: string;
  tone: ProcessTone;
  statusText: string;
  /** What the execution view reads; absent when the question has no execution facts. */
  execution?: {
    questionId: string;
    execution?: AnalysisEvidenceQuestion["execution"] | DeepAnalysisQuestion["execution"];
    executionDeferred?: true;
  };
}

function dimensionQuestionRow(dimensionId: string, question: DeepAnalysisQuestion): QuestionRow {
  return {
    key: `${dimensionId}:${question.id}`,
    text: question.text,
    tone: question.status === "stopped" ? "cancelled" : question.status,
    statusText: question.statusText || "",
    execution: question.execution
      ? {
          questionId: question.id,
          execution: question.execution,
          executionDeferred: question.executionDeferred,
        }
      : undefined,
  };
}

const dimensions = computed(() =>
  isLoop.value
    ? []
    : buildDimensionTree(props.payload.activities).map((dimension) => {
        const tone = dimensionTone(dimension);
        const questions = dimension.questions.map((q) =>
          dimensionQuestionRow(dimension.dimensionId, q)
        );
        return {
          key: dimension.dimensionId,
          name: dimension.dimName,
          reason: dimension.reason,
          tone,
          completed: questions.filter((q) => q.tone === "completed").length,
          failed: questions.filter((q) => q.tone === "failed").length,
          questions,
          // Work in progress and failures are open until the user decides otherwise.
          openByDefault: tone === "running" || tone === "failed",
        };
      })
);

// ---- Loop execution: the activity stream in acceptance order ----

type LoopEntry =
  | { type: "narrative"; key: string; text: string }
  | {
      type: "step";
      key: string;
      activity: AnalysisActivity;
      label: string;
      summary: string;
      fields: LoopDetailField[];
      questions: QuestionRow[];
      expandable: boolean;
    };

function evidenceQuestionRow(activityId: string, question: AnalysisEvidenceQuestion): QuestionRow {
  return {
    key: `${activityId}:${question.questionId}`,
    text: question.question,
    tone: question.status,
    statusText: loopQuestionStatusText(question, t),
    execution: question.execution ? question : undefined,
  };
}

const loopEntries = computed<LoopEntry[]>(() =>
  isLoop.value
    ? props.payload.activities.flatMap((activity): LoopEntry[] => {
        if (activity.kind === "narrative") {
          const text = activity.narrative?.trim();
          return text ? [{ type: "narrative", key: activity.activityId, text }] : [];
        }
        const fields = loopActivityDetailFields(activity, t);
        const questions = (activity.questions || []).map((q) =>
          evidenceQuestionRow(activity.activityId, q)
        );
        return [
          {
            type: "step",
            key: activity.activityId,
            activity,
            label: loopActivityLabel(activity.kind as LoopStepKind, t),
            summary: loopActivitySummary(activity, t),
            fields,
            questions,
            expandable: fields.length > 0 || questions.length > 0 || activity.kind === "dispatch",
          },
        ];
      })
    : []
);

// ---- Header overview ----

const overview = computed(() => {
  const parts: Array<{ text: string; issue?: boolean }> = [];
  const progress = props.payload.runState.progress;
  if (isLoop.value) {
    parts.push({ text: t("analysis.loopActivityCount", { done: progress.done }) });
  } else if (dimensions.value.length) {
    const questions = dimensions.value.flatMap((d) => d.questions);
    parts.push({
      text: t("analysis.dimensionQuestionProgress", {
        done: questions.filter((q) => q.tone === "completed").length,
        total: questions.length,
      }),
    });
  }
  if (status.value === "running") {
    const current = progress.label || stages.value.find((s) => s.tone === "running")?.name;
    if (current) parts.push({ text: t("analysis.loopCurrentActivity", { name: current }) });
  }
  if (issues.value.failedQuestions)
    parts.push({
      text: t("analysis.processFailedQuestions", { n: issues.value.failedQuestions }),
      issue: true,
    });
  if (issues.value.failedActivities)
    parts.push({
      text: t("analysis.processFailedActivities", { n: issues.value.failedActivities }),
      issue: true,
    });
  return parts;
});

// ---- Dispatch worker traces, read on demand ----

interface SubagentTraceState {
  expanded: boolean;
  loading: boolean;
  trace?: LoopSubagentTrace;
  error?: boolean;
}

const subagentTraceStates = ref<Record<string, SubagentTraceState>>({});

async function toggleSubagentTrace(activityId: string) {
  const current = subagentTraceStates.value[activityId];
  if (current?.expanded || current?.trace) {
    subagentTraceStates.value = {
      ...subagentTraceStates.value,
      [activityId]: { ...current, expanded: !current.expanded },
    };
    return;
  }
  const taskId = props.taskId;
  if (!taskId) return;
  subagentTraceStates.value = {
    ...subagentTraceStates.value,
    [activityId]: { expanded: true, loading: true },
  };
  try {
    const trace = await analysisTaskApi.getLoopSubagentTrace(taskId, activityId);
    if (props.taskId !== taskId) return;
    subagentTraceStates.value = {
      ...subagentTraceStates.value,
      [activityId]: { expanded: true, loading: false, trace },
    };
  } catch {
    if (props.taskId !== taskId) return;
    subagentTraceStates.value = {
      ...subagentTraceStates.value,
      [activityId]: { expanded: true, loading: false, error: true },
    };
  }
}
</script>

<template>
  <section class="turn-process" :class="`is-${status}`">
    <button
      type="button"
      class="process-header"
      :aria-expanded="expanded"
      @click="toggleProcess"
    >
      <component
        :is="TONE_ICONS[status]"
        class="process-icon tone-icon"
        :class="[`tone-${status}`, { spin: status === 'running' }]"
        :size="15"
        aria-hidden="true"
      />
      <span class="process-title">{{ t("analysis.workProcess") }}</span>
      <span class="process-state tone-text" :class="`tone-${status}`">
        {{ t(TONE_TEXT_KEYS[status]) }}
      </span>
      <span
        v-for="(part, index) in overview"
        :key="index"
        class="process-meta"
        :class="{ issue: part.issue }"
      >
        {{ part.text }}
      </span>
      <component :is="expanded ? ChevronDown : ChevronRight" class="process-chevron" :size="15" />
    </button>

    <div v-if="expanded" class="process-body">
      <!-- Loop execution: narrative and steps keep their recorded order -->
      <ol v-if="isLoop" class="process-list">
        <li
          v-for="entry in loopEntries"
          :key="entry.key"
          class="process-item"
          :class="entry.type"
        >
          <p v-if="entry.type === 'narrative'" class="process-narrative">{{ entry.text }}</p>
          <template v-else>
            <button
              type="button"
              class="process-row"
              :disabled="!entry.expandable"
              :aria-expanded="entry.expandable ? isItemExpanded(entry.key) : undefined"
              @click="toggleItem(entry.key)"
            >
              <component
                :is="TONE_ICONS[entry.activity.status]"
                class="tone-icon"
                :class="[`tone-${entry.activity.status}`, { spin: entry.activity.status === 'running' }]"
                :size="14"
                aria-hidden="true"
              />
              <span class="row-label">{{ entry.label }}</span>
              <span v-if="entry.summary" class="row-summary">{{ entry.summary }}</span>
              <span
                v-if="entry.activity.status !== 'completed'"
                class="row-state tone-text"
                :class="`tone-${entry.activity.status}`"
              >
                {{ t(TONE_TEXT_KEYS[entry.activity.status]) }}
              </span>
              <component
                :is="isItemExpanded(entry.key) ? ChevronDown : ChevronRight"
                v-if="entry.expandable"
                class="row-chevron"
                :size="14"
              />
            </button>
            <p v-if="entry.activity.error" class="row-error">{{ entry.activity.error }}</p>

            <div v-if="entry.expandable && isItemExpanded(entry.key)" class="row-details">
              <div v-for="field in entry.fields" :key="field.label" class="detail-field">
                <span>{{ field.label }}</span>
                <pre v-if="field.raw">{{ field.value }}</pre>
                <p v-else>{{ field.value }}</p>
              </div>

              <ul v-if="entry.questions.length" class="question-list">
                <li
                  v-for="question in entry.questions"
                  :key="question.key"
                  class="question-row"
                  :class="`tone-${question.tone}`"
                >
                  <div class="question-line">
                    <component
                      :is="TONE_ICONS[question.tone]"
                      class="tone-icon"
                      :class="[`tone-${question.tone}`, { spin: question.tone === 'running' }]"
                      :size="12"
                      aria-hidden="true"
                    />
                    <span class="question-text">{{ question.text }}</span>
                    <span
                      v-if="question.statusText"
                      class="question-status"
                      :class="{ error: question.tone === 'failed' }"
                    >
                      {{ question.statusText }}
                    </span>
                    <button
                      v-if="question.execution"
                      type="button"
                      class="link-toggle question-toggle"
                      :aria-expanded="isItemExpanded(question.key)"
                      @click="toggleItem(question.key)"
                    >
                      {{ isItemExpanded(question.key) ? t("common.collapse") : t("common.view") }}
                    </button>
                  </div>
                  <QuestionExecution
                    v-if="question.execution && isItemExpanded(question.key)"
                    class="question-execution"
                    :question="question.execution"
                    :scope="executionScope"
                  />
                </li>
              </ul>

              <template v-if="entry.activity.kind === 'dispatch'">
                <div class="detail-field">
                  <span>{{ t("analysis.loopDispatchTopic") }}</span>
                  <p>{{ entry.activity.topic || "-" }}</p>
                </div>
                <div v-if="entry.activity.workerSummary" class="detail-field">
                  <span>{{ t("analysis.loopDispatchSummary") }}</span>
                  <p>{{ entry.activity.workerSummary }}</p>
                </div>
                <button
                  v-if="entry.activity.status !== 'running' && taskId"
                  type="button"
                  class="link-toggle"
                  :aria-expanded="!!subagentTraceStates[entry.key]?.expanded"
                  :disabled="subagentTraceStates[entry.key]?.loading"
                  @click="toggleSubagentTrace(entry.key)"
                >
                  <Loader2 v-if="subagentTraceStates[entry.key]?.loading" class="spin" :size="13" />
                  <ChevronDown v-else-if="subagentTraceStates[entry.key]?.expanded" :size="13" />
                  <ChevronRight v-else :size="13" />
                  {{
                    subagentTraceStates[entry.key]?.expanded ? t("common.collapse") : t("common.view")
                  }}
                  {{ t("analysis.loopWorkerTrace") }}
                </button>
                <div v-if="subagentTraceStates[entry.key]?.expanded" class="worker-trace">
                  <div v-if="subagentTraceStates[entry.key]?.error" class="trace-note error">
                    {{ t("analysis.loopWorkerTraceLoadFailed") }}
                  </div>
                  <div
                    v-else-if="subagentTraceStates[entry.key]?.trace?.error"
                    class="trace-note error"
                  >
                    {{ subagentTraceStates[entry.key]?.trace?.error }}
                  </div>
                  <div
                    v-else-if="
                      !subagentTraceStates[entry.key]?.loading &&
                      !subagentTraceStates[entry.key]?.trace?.messages.length
                    "
                    class="trace-note"
                  >
                    {{ t("common.noData") }}
                  </div>
                  <AgentMessageTrace
                    v-if="subagentTraceStates[entry.key]?.trace?.messages.length"
                    :messages="subagentTraceStates[entry.key]?.trace?.messages || []"
                    variant="worker"
                  />
                </div>
              </template>
            </div>
          </template>
        </li>
      </ol>

      <!-- Dimension orchestration: stages, then dimensions -> questions -> evidence -->
      <template v-else>
        <ol class="stage-list">
          <li
            v-for="stage in stages"
            :key="stage.name"
            class="stage-item tone-text"
            :class="`tone-${stage.tone}`"
          >
            <component
              :is="TONE_ICONS[stage.tone]"
              class="tone-icon"
              :class="[`tone-${stage.tone}`, { spin: stage.tone === 'running' }]"
              :size="13"
              aria-hidden="true"
            />
            <span>{{ stage.name }}</span>
            <span class="visually-hidden">{{ t(TONE_TEXT_KEYS[stage.tone]) }}</span>
          </li>
        </ol>

        <ol v-if="dimensions.length" class="process-list">
          <li v-for="(dimension, index) in dimensions" :key="dimension.key" class="process-item">
            <button
              type="button"
              class="process-row dimension-row"
              :aria-expanded="isItemExpanded(dimension.key, dimension.openByDefault)"
              @click="toggleItem(dimension.key, dimension.openByDefault)"
            >
              <component
                :is="TONE_ICONS[dimension.tone]"
                class="tone-icon"
                :class="[`tone-${dimension.tone}`, { spin: dimension.tone === 'running' }]"
                :size="14"
                aria-hidden="true"
              />
              <span class="row-label">{{ index + 1 }}. {{ dimension.name }}</span>
              <span class="row-summary">
                {{ dimension.completed }}/{{ dimension.questions.length }}
              </span>
              <span v-if="dimension.failed" class="row-state tone-text tone-failed">
                {{ t("analysis.processFailedQuestions", { n: dimension.failed }) }}
              </span>
              <span
                v-else-if="dimension.tone === 'cancelled'"
                class="row-state tone-text tone-cancelled"
              >
                {{ t("common.stopped") }}
              </span>
              <component
                :is="
                  isItemExpanded(dimension.key, dimension.openByDefault) ? ChevronDown : ChevronRight
                "
                class="row-chevron"
                :size="14"
              />
            </button>
            <p v-if="dimension.reason" class="row-reason">{{ dimension.reason }}</p>
            <ul
              v-if="isItemExpanded(dimension.key, dimension.openByDefault) && dimension.questions.length"
              class="question-list"
            >
              <li
                v-for="question in dimension.questions"
                :key="question.key"
                class="question-row"
                :class="`tone-${question.tone}`"
              >
                <div class="question-line">
                  <component
                    :is="TONE_ICONS[question.tone]"
                    class="tone-icon"
                    :class="[`tone-${question.tone}`, { spin: question.tone === 'running' }]"
                    :size="12"
                    aria-hidden="true"
                  />
                  <span class="question-text">{{ question.text }}</span>
                  <span
                    v-if="question.statusText"
                    class="question-status"
                    :class="{ error: question.tone === 'failed' }"
                  >
                    {{ question.statusText }}
                  </span>
                  <button
                    v-if="question.execution"
                    type="button"
                    class="link-toggle question-toggle"
                    :aria-expanded="isItemExpanded(question.key)"
                    @click="toggleItem(question.key)"
                  >
                    {{ isItemExpanded(question.key) ? t("common.collapse") : t("common.view") }}
                  </button>
                </div>
                <QuestionExecution
                  v-if="question.execution && isItemExpanded(question.key)"
                  class="question-execution"
                  :question="question.execution"
                  :scope="executionScope"
                />
              </li>
            </ul>
          </li>
        </ol>
      </template>
    </div>
  </section>
</template>

<style scoped>
.turn-process {
  margin-bottom: 16px;
  border-left: 2px solid var(--el-border-color-light);
  padding-left: 12px;
}

.turn-process.is-running {
  border-left-color: var(--el-color-primary-light-5);
}

.turn-process.is-failed {
  border-left-color: var(--el-color-danger-light-5);
}

.process-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  width: 100%;
  min-height: 32px;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.process-title {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.process-meta {
  font-size: 12px;
}

.process-meta::before {
  content: "·";
  margin-right: 8px;
  color: var(--el-text-color-placeholder);
}

.process-meta.issue {
  color: var(--el-color-danger);
}

.process-chevron,
.row-chevron {
  flex-shrink: 0;
  margin-left: auto;
  color: var(--el-text-color-secondary);
}

.process-body {
  padding: 4px 0 2px;
}

.process-list,
.stage-list,
.question-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.process-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.process-narrative {
  margin: 6px 0;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.process-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 30px;
  padding: 4px 6px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
}

.process-row:hover:not(:disabled) {
  background: var(--el-fill-color-light);
}

.process-row:disabled {
  cursor: default;
}

/* Business names and summaries wrap instead of truncating; state and chevron stay reachable. */
.row-label,
.row-summary {
  min-width: 0;
  overflow-wrap: anywhere;
}

.row-label {
  color: var(--el-text-color-primary);
}

.row-summary {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.row-state {
  flex-shrink: 0;
  font-size: 12px;
}

.row-error,
.row-reason {
  margin: 0 0 4px 28px;
  font-size: 12px;
  line-height: 1.6;
  word-break: break-word;
}

.row-error {
  color: var(--el-color-danger);
}

.row-reason {
  color: var(--el-text-color-secondary);
}

.row-details {
  margin: 2px 0 8px 28px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.detail-field > span {
  font-size: 11px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.detail-field > p,
.detail-field > pre {
  margin: 2px 0 0;
  color: var(--el-text-color-regular);
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-field > pre {
  padding: 6px 8px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  font-family: var(--el-font-family-mono, ui-monospace, monospace);
  overflow-x: auto;
}

.stage-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin: 0 0 6px 6px;
  font-size: 12px;
}

.stage-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.process-item > .question-list {
  margin: 0 0 6px 28px;
}

.question-row {
  font-size: 12px;
}

.question-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 26px;
}

.question-text {
  flex: 1;
  min-width: 0;
  color: var(--el-text-color-regular);
  word-break: break-word;
}

.question-status {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.question-status.error {
  color: var(--el-color-danger);
}

.question-execution {
  min-width: 0;
  margin: 4px 0 6px 18px;
}

.link-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  align-self: flex-start;
  min-height: 24px;
  padding: 0 4px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--el-color-primary);
  font-size: 12px;
  cursor: pointer;
}

.link-toggle:hover:not(:disabled) {
  background: var(--el-color-primary-light-9);
}

.link-toggle:disabled {
  cursor: wait;
  opacity: 0.72;
}

.process-header:focus-visible,
.process-row:focus-visible,
.link-toggle:focus-visible {
  outline: 2px solid var(--el-color-primary-light-3);
  outline-offset: 2px;
}

.worker-trace {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trace-note {
  padding: 4px 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.trace-note.error {
  color: var(--el-color-danger);
}

.tone-icon {
  flex-shrink: 0;
}

.tone-completed {
  color: var(--el-color-success);
}

.tone-running {
  color: var(--el-color-primary);
}

.tone-failed {
  color: var(--el-color-danger);
}

.tone-cancelled {
  color: var(--el-color-warning);
}

.tone-pending {
  color: var(--el-text-color-placeholder);
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .spin {
    animation: none;
  }
}

@media (max-width: 700px) {
  :where(html.workbench-mobile-navigation) .row-details,
  :where(html.workbench-mobile-navigation) .process-item > .question-list,
  :where(html.workbench-mobile-navigation) .row-error,
  :where(html.workbench-mobile-navigation) .row-reason {
    margin-left: 12px;
  }
}
</style>

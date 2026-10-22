<script setup lang="ts">
import type { EgressMessage } from "@ontomato/contracts/agent-egress";
import type { DeepAnalysisTaskPayload } from "@ontomato/contracts/analysis-presentation";

import { computed, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useAnalysisStore } from "../stores/analysis";
import {
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  CircleSlash,
  CircleX,
  Clock,
  FileText,
} from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { Message } from "../../../types/chat";
import { renderMarkdown } from "../../../utils/markdown";
import AgentMessageTrace from "./AgentMessageTrace.vue";
import AnalysisTurnProcess from "./AnalysisTurnProcess.vue";
import type { QuestionExecutionScope } from "./QuestionExecution.vue";
import TurnDiagnoseButton from "../../diagnosis/components/TurnDiagnoseButton.vue";
import { workbenchContent } from "../../../content";

const emit = defineEmits<{
  /** Asks the host to switch to its report view; the host owns which view is shown. */
  openReport: [];
}>();

const runSwitcherCopy = workbenchContent().text.runSwitcher;

const { t } = useI18n();

const store = useAnalysisStore();
const { currentTask: task, taskRuns, taskRunIndex, hasReportArtifact } = storeToRefs(store);
const {
  displayMessages: messages,
  displayStreamingSnapshot: streamingSnapshot,
  displayIsLoading: isLoading,
} = storeToRefs(store);
const runCount = computed(() => taskRuns.value.length);
const runIndex = computed(() => taskRunIndex.value);

function selectRun(index: number) {
  store.selectRun(index);
}

const expandedTurnTraces = ref<Record<string, boolean>>({});

watch(
  () => task.value?.id,
  () => {
    expandedTurnTraces.value = {};
  }
);

/** Rendered turn by turn: each turn's user message, execution artifacts, and reply are grouped, and later turns don't overwrite earlier ones. */
interface TurnGroup {
  user: Message | null;
  assistant: Message | null;
  payload: DeepAnalysisTaskPayload | null;
  /** Public messages and tool results already egress-filtered for this turn; only from this turn's snapshot. */
  trace: EgressMessage[];
  /** This turn is streaming: the payload comes from the streaming snapshot, or the standard follow-up body is still growing. */
  streaming: boolean;
  streamingText: string;
  /** Acceptance sequence of the turn this group shows, when its snapshot carries one. */
  requestSeq?: number;
  /** Run the turn's snapshot belongs to, when it carries one. */
  runId?: string;
}

const turnGroups = computed<TurnGroup[]>(() => {
  const groups: TurnGroup[] = [];
  for (const message of messages.value) {
    if (message.role === "user") {
      groups.push({
        user: message,
        assistant: null,
        payload: null,
        trace: [],
        streaming: false,
        streamingText: "",
      });
      continue;
    }
    let group = groups[groups.length - 1];
    if (!group || group.assistant) {
      group = {
        user: null,
        assistant: null,
        payload: null,
        trace: [],
        streaming: false,
        streamingText: "",
      };
      groups.push(group);
    }
    group.assistant = message;
    group.requestSeq = message.snapshot?.requestSeq;
    group.runId = message.snapshot?.runId;
    if (message.snapshot?.mode === "deep-analysis" && message.snapshot.deepAnalysis) {
      group.payload = message.snapshot.deepAnalysis;
    }
    if (message.snapshot?.trace) group.trace = message.snapshot.trace;
  }

  const streaming = streamingSnapshot.value;
  if (streaming) {
    let group = groups[groups.length - 1];
    // Only attach to the current turn that has no reply yet; when the previous turn is complete, don't overwrite its artifacts.
    if (!group || group.assistant) {
      group = {
        user: null,
        assistant: null,
        payload: null,
        trace: [],
        streaming: false,
        streamingText: "",
      };
      groups.push(group);
    }
    group.streaming = true;
    group.requestSeq = streaming.requestSeq;
    group.runId = streaming.runId;
    if (streaming.trace) group.trace = streaming.trace;
    if (streaming.mode === "deep-analysis" && streaming.deepAnalysis) {
      group.payload = streaming.deepAnalysis;
    } else {
      group.streamingText = streaming.primaryText || "";
    }
  }
  return groups;
});

/** First occurrence position of a follow-up record: the first standard follow-up turn after a turn with execution artifacts. */
const followUpDividerIndex = computed(() => {
  let seenExecution = false;
  for (let index = 0; index < turnGroups.value.length; index++) {
    const group = turnGroups.value[index];
    if (group.payload) {
      seenExecution = true;
      continue;
    }
    if (seenExecution) return index;
  }
  return -1;
});

/** Only report tasks switch by run history; regular continuous conversations always show all turns. Judged by the task's own delivery mode. */
const isReportTask = computed(() => task.value?.reportDeliverableEnabled !== false);

/**
 * Where a group's deferred question facts are read: a report addresses the run being viewed (the
 * current one or a selected historical run); a regular session addresses the group's own turn.
 */
function executionScope(group: TurnGroup): QuestionExecutionScope | undefined {
  const taskId = task.value?.id;
  if (!taskId) return undefined;
  if (!isReportTask.value) return { taskId, requestSeq: group.requestSeq };
  return { taskId, runId: store.isSelectedLatestRun ? undefined : store.selectedRun?.id };
}

const showEmpty = computed(
  () => messages.value.length === 0 && !isLoading.value && !streamingSnapshot.value
);

/**
 * This turn's reply body only takes this turn's own artifacts or reply; it doesn't use the task's
 * current fields to backfill the last turn, otherwise the previous turn's answer would leak into a
 * new turn that hasn't produced an answer yet.
 */
function groupAnswer(group: TurnGroup): string {
  // The deep analysis body of a report task is delivered only in the report view; even if that run
  // is cancelled and produces no report artifact, partial body must not be rolled back from the
  // snapshot into the chat bubble.
  if (group.payload && isReportTask.value) return "";
  return group.payload?.finalAnswer || group.assistant?.content || "";
}

interface TurnOutcome {
  tone: "completed" | "failed" | "cancelled";
  text: string;
  error?: string;
  /** The report view has content for this run, so the turn can point to it. */
  report?: boolean;
}

/**
 * How an execution turn ended, read only from its own run state. A completed turn needs no notice of
 * its own unless its report is ready; whether a report exists is the store's existing fact.
 */
function turnOutcome(group: TurnGroup): TurnOutcome | null {
  const runState = group.payload?.runState;
  if (!runState) return null;
  if (runState.status === "failed")
    return { tone: "failed", text: t("analysis.turnFailed"), error: runState.error };
  if (runState.status === "cancelled")
    return { tone: "cancelled", text: t("analysis.turnStopped"), error: runState.error };
  if (runState.status === "completed" && isReportTask.value && hasReportArtifact.value)
    return { tone: "completed", text: t("analysis.reportReadyHint"), report: true };
  return null;
}

/**
 * Stable identity of a rendered turn. Message ids are rebuilt when live turns settle into history
 * and every historical run reuses the same ids, so the key comes from the task, the historical run
 * being viewed, and the turn's own acceptance sequence (or run). Legacy turns without either fall
 * back to their position in the conversation.
 */
function turnKey(group: TurnGroup, index: number): string {
  const view =
    isReportTask.value && !store.isSelectedLatestRun ? `run:${store.selectedRun?.id}` : "current";
  const turn =
    group.requestSeq !== undefined
      ? `seq:${group.requestSeq}`
      : group.runId
        ? `run:${group.runId}`
        : `pos:${index}`;
  return `${task.value?.id}|${view}|${turn}`;
}

/** Per-turn display facts, derived once per update instead of in the template. */
const turnViews = computed(() =>
  turnGroups.value.map((group, index) => ({
    key: turnKey(group, index),
    group,
    answer: groupAnswer(group),
    outcome: turnOutcome(group),
    scope: executionScope(group),
  }))
);

const OUTCOME_ICONS = {
  completed: CheckCircle2,
  failed: CircleX,
  cancelled: CircleSlash,
} as const;

function toggleTurnTrace(key: string) {
  expandedTurnTraces.value = {
    ...expandedTurnTraces.value,
    [key]: !expandedTurnTraces.value[key],
  };
}
</script>

<template>
  <div class="analysis-chat-view">
    <div v-if="showEmpty" class="chat-empty">
      <div class="ai-skeleton">
        <el-skeleton animated :rows="10" />
      </div>
    </div>

    <div v-else class="messages-scroll">
      <div class="messages-inner">
        <!-- Each turn: request, work process, reply, how it ended, then its secondary tools -->
        <template v-for="({ key, group, answer, outcome, scope }, gi) in turnViews" :key="key">
          <div v-if="gi === followUpDividerIndex" class="follow-up-divider">
            <span>{{ t("analysis.followUpRecord") }}</span>
          </div>

          <div v-if="group.user" class="chat-msg user">
            <div class="chat-bubble user">{{ group.user.content }}</div>
          </div>

          <div
            v-if="
              gi === 0 &&
              group.user &&
              isReportTask &&
              (runCount || 0) > 1 &&
              task?.status !== 'running'
            "
            class="run-switcher-row"
          >
            <div class="run-switcher" :aria-label="runSwitcherCopy.label">
              <button
                :disabled="runIndex === 0"
                :title="runSwitcherCopy.previous"
                @click="selectRun((runIndex || 0) - 1)"
              >
                ‹
              </button>
              <span class="run-index">{{ (runIndex || 0) + 1 }} / {{ runCount }}</span>
              <button
                :disabled="runIndex === (runCount || 0) - 1"
                :title="runSwitcherCopy.next"
                @click="selectRun((runIndex || 0) + 1)"
              >
                ›
              </button>
            </div>
          </div>

          <AnalysisTurnProcess
            v-if="group.payload"
            :key="key"
            :payload="group.payload"
            :execution-scope="scope"
            :task-id="task?.id"
          />

          <div v-if="answer" class="chat-msg assistant">
            <div class="chat-bubble assistant">
              <div class="markdown-body" v-html="renderMarkdown(answer)"></div>
            </div>
          </div>

          <!-- Follow-up turn streaming tail: show the typing cursor while the body is still growing -->
          <div v-if="group.streaming && group.streamingText !== ''" class="chat-msg assistant">
            <div class="chat-bubble assistant streaming">
              <div class="markdown-body" v-html="renderMarkdown(group.streamingText)"></div>
              <span class="typing-cursor">|</span>
            </div>
          </div>

          <div
            v-if="outcome"
            class="turn-outcome"
            :class="`tone-${outcome.tone}`"
            :role="outcome.tone === 'failed' ? 'alert' : undefined"
          >
            <component
              :is="OUTCOME_ICONS[outcome.tone]"
              class="outcome-icon"
              :size="15"
              aria-hidden="true"
            />
            <span class="outcome-text">
              {{ outcome.text }}
              <span v-if="outcome.error" class="outcome-error">{{ outcome.error }}</span>
            </span>
            <button
              v-if="outcome.report"
              type="button"
              class="outcome-action"
              @click="emit('openReport')"
            >
              <FileText :size="14" aria-hidden="true" />
              {{ t("analysis.viewReport") }}
            </button>
          </div>

          <!-- Secondary tools of this turn: its raw public messages and tool results, and the diagnosis entry -->
          <div v-if="group.trace.length || group.assistant?.snapshot" class="turn-tools">
            <button
              v-if="group.trace.length"
              type="button"
              class="turn-trace-toggle"
              :aria-expanded="!!expandedTurnTraces[key]"
              @click="toggleTurnTrace(key)"
            >
              <ChevronDown v-if="expandedTurnTraces[key]" :size="13" />
              <ChevronRight v-else :size="13" />
              {{ t("harness.messagesAndTools") }}
            </button>
            <TurnDiagnoseButton
              v-if="group.assistant?.snapshot"
              class="ui-icon-btn turn-diagnose-btn"
              :turn="group.assistant.snapshot"
            />
          </div>
          <div v-if="group.trace.length && expandedTurnTraces[key]" class="turn-trace">
            <AgentMessageTrace :messages="group.trace" variant="turn" />
          </div>
        </template>

        <!-- Scheduled task notice -->
        <div
          v-if="
            messages.length === 0 && !isLoading && !streamingSnapshot && task?.status === 'pending'
          "
          class="scheduled-notice"
        >
          <Clock :size="15" class="outcome-icon" aria-hidden="true" />
          <span>
            {{ t("analysis.scheduledTaskHint") }}
            <br />
            {{ t("analysis.scheduledLastResult") }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.analysis-chat-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: color-mix(in srgb, var(--el-bg-color) 30%, var(--el-bg-color-page) 70%);
}

.chat-empty {
  flex: 1;
  width: 100%;
}

.ai-skeleton {
  width: 100%;
  padding: 40px;
}

.messages-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.messages-inner {
  padding: 20px 24px 0;
}

.chat-msg {
  margin-bottom: 16px;
  animation: fadeIn 0.3s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .chat-msg {
    animation: none;
  }
}

.chat-msg.user {
  display: flex;
  justify-content: flex-end;
}

.run-switcher-row {
  display: flex;
  justify-content: flex-end;
  margin: -10px 4px 10px 0;
}

.run-switcher {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.run-switcher .run-index {
  line-height: 28px;
}

.run-switcher button {
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: inherit;
  font-size: 18px;
  line-height: 24px;
  cursor: pointer;
}

.run-switcher button:hover:not(:disabled) {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.run-switcher button:disabled {
  opacity: 0.3;
  cursor: default;
}

.chat-bubble {
  max-width: 85%;
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
}

.chat-bubble.user {
  background: var(--el-color-primary);
  color: var(--on-primary);
  border-radius: 12px 12px 4px 12px;
}

.chat-bubble.assistant {
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  border: 1px solid var(--el-border-color-lighter);
  font-size: 14px;
  line-height: 1.7;
}

.chat-bubble.assistant :deep(.markdown-body) {
  font-size: 14px;
  line-height: 1.7;
}

.chat-bubble.assistant :deep(.markdown-body p) {
  margin: 0 0 8px;
}

.chat-bubble.assistant :deep(.markdown-body p:last-child) {
  margin-bottom: 0;
}

.chat-bubble.assistant :deep(.markdown-body code) {
  background: var(--el-fill-color);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 13px;
}

.chat-bubble.assistant :deep(.markdown-body pre) {
  background: var(--el-fill-color);
  padding: 8px 12px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
  margin: 8px 0;
}

.chat-bubble.assistant :deep(.markdown-body pre code) {
  background: none;
  padding: 0;
}

.chat-bubble.assistant.streaming {
  border-color: var(--el-color-primary-light-5);
}

.typing-cursor {
  display: inline-block;
  animation: blink 1s step-end infinite;
  color: var(--el-color-primary);
  font-weight: bold;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

/* How a turn ended: a quiet status line, not another bubble */
.turn-outcome,
.scheduled-notice {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 4px 8px;
  margin-bottom: 12px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
}

.outcome-icon {
  flex-shrink: 0;
  margin-top: 3px;
}

.turn-outcome.tone-completed .outcome-icon {
  color: var(--el-color-success);
}

.turn-outcome.tone-failed .outcome-icon,
.outcome-error {
  color: var(--el-color-danger);
}

.turn-outcome.tone-cancelled .outcome-icon,
.scheduled-notice .outcome-icon {
  color: var(--el-color-warning);
}

.outcome-text {
  flex: 1;
  min-width: 0;
  word-break: break-word;
}

.outcome-error {
  display: block;
  font-size: 12px;
}

.outcome-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  padding: 2px 10px;
  border: 1px solid var(--el-color-primary-light-5);
  border-radius: 6px;
  background: transparent;
  color: var(--el-color-primary);
  font-size: 12px;
  cursor: pointer;
}

.outcome-action:hover {
  background: var(--el-color-primary-light-9);
}

/* Secondary per-turn tools */
.turn-tools {
  display: flex;
  align-items: center;
  gap: 4px;
  margin: -6px 0 16px;
}

.turn-trace-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 28px;
  padding: 2px 6px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  cursor: pointer;
}

.turn-trace-toggle:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.turn-diagnose-btn {
  display: flex;
  border-radius: 6px;
}

.turn-diagnose-btn:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}

.turn-trace-toggle:focus-visible,
.outcome-action:focus-visible {
  outline: 2px solid var(--el-color-primary-light-3);
  outline-offset: 2px;
}

.turn-trace {
  margin: -8px 0 16px;
}

.follow-up-divider {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 24px 0 16px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.follow-up-divider::before,
.follow-up-divider::after {
  content: "";
  flex: 1;
  height: 1px;
  background: var(--el-border-color-light);
}

.messages-scroll::-webkit-scrollbar {
  width: 4px;
}

.messages-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.messages-scroll::-webkit-scrollbar-thumb {
  background: var(--el-border-color-dark);
  border-radius: 4px;
}

.messages-scroll::-webkit-scrollbar-thumb:hover {
  background: var(--el-text-color-secondary);
}

@media (max-width: 700px) {
  :where(html.workbench-mobile-navigation) .messages-inner {
    padding: 12px 10px 0;
  }
  :where(html.workbench-mobile-navigation) .ai-skeleton {
    padding: 20px 12px;
  }
  :where(html.workbench-mobile-navigation) .chat-msg {
    margin-bottom: 12px;
  }
  :where(html.workbench-mobile-navigation) .chat-bubble {
    max-width: 94%;
    padding: 10px 12px;
  }
  :where(html.workbench-mobile-navigation) .run-switcher-row {
    margin-right: 0;
  }
}
</style>

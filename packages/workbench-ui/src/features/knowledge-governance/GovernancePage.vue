<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { ArrowLeft, BookOpen, FileText } from "lucide-vue-next";
import AgentChatInput from "../../components/agent-chat/AgentChatInput.vue";
import AgentMessage from "../../components/agent-chat/AgentMessage.vue";
import { useGovernance } from "./useGovernance";
import { governanceReturnPath } from "./return-path";

const { t } = useI18n();
const router = useRouter(),
  route = useRoute();
const {
  session,
  messages,
  running,
  rounds,
  loading,
  sending,
  stopping,
  error,
  streamLost,
  source,
  select,
  reload,
  send,
  stop,
  readSource,
} = useGovernance();
const input = ref(""),
  panel = ref<"report" | "sources" | null>(null);
const conversation = ref<HTMLElement | null>(null);
const panelElement = ref<HTMLElement | null>(null);
// Review counts appear only once the Agent has recorded an actual review scope.
const progress = computed(() => {
  const work = session.value?.work;
  return work && work.knowledgeTotal !== null
    ? t("governance.progress", { done: work.reviewedKnowledgeIds.length, total: work.knowledgeTotal })
    : "";
});
const statusText = computed(() => {
  // A lost stream outranks a pending Stop: nothing is being followed any more.
  if (streamLost.value) return t("governance.disconnected");
  if (stopping.value) return t("governance.stopping");
  if (running.value) return t("governance.running");
  return t(`governance.${session.value!.status}`);
});
watch(
  () => route.params.sessionId,
  (id) => {
    const next = typeof id === "string" && id ? id : undefined;
    // The route catching up to the round just created keeps the draft and panel.
    if (next !== session.value?.id) {
      input.value = "";
      panel.value = null;
    }
    void select(next);
  },
  { immediate: true }
);
watch(messages, async () => {
  const element = conversation.value;
  const nearEnd = !element || element.scrollHeight - element.scrollTop - element.clientHeight < 160;
  await nextTick();
  if (nearEnd && conversation.value) conversation.value.scrollTop = conversation.value.scrollHeight;
});
watch(panel, async (value) => {
  if (!value || !window.matchMedia("(max-width: 800px)").matches) return;
  await nextTick();
  panelElement.value?.scrollIntoView({ block: "start" });
});
// Switching sessions keeps the from query so Back still returns to the entry page.
function sessionLocation(sessionId?: string) {
  return {
    name: "KnowledgeGovernance",
    params: sessionId ? { sessionId } : {},
    query: { from: route.query.from },
  };
}
// Typed text and suggestions are the same user message; the first one creates the round.
async function submit(text: string, fromInput: boolean) {
  const id = await send(text);
  if (!id) return;
  // Clear only what was sent; text typed while the request was pending stays as the draft.
  if (fromInput && input.value.startsWith(text)) input.value = input.value.slice(text.length);
  if (route.params.sessionId !== id) await router.push(sessionLocation(id));
}
function showSource(id: string) {
  panel.value = "sources";
  void readSource(id);
}
function closeHistory(event: MouseEvent) {
  (event.currentTarget as HTMLElement).closest("details")!.open = false;
}
</script>

<template>
  <section class="kg-page">
    <header class="kg-header">
      <div class="kg-heading">
        <button
          class="kg-icon"
          :aria-label="t('governance.back')"
          @click="router.push(governanceReturnPath(route.query))"
        >
          <ArrowLeft :size="18" />
        </button>
        <h1>{{ t("governance.title") }}</h1>
      </div>
      <div class="kg-toolbar">
        <template v-if="session">
          <button
            class="kg-button"
            :disabled="sending || running"
            @click="router.push(sessionLocation())"
          >
            {{ t("governance.start") }}
          </button>
          <button
            class="kg-button"
            :aria-pressed="panel === 'sources'"
            @click="panel = panel === 'sources' ? null : 'sources'"
          >
            <BookOpen :size="16" />
            {{ t("governance.sources") }}
          </button>
          <button
            class="kg-button"
            :aria-pressed="panel === 'report'"
            @click="panel = panel === 'report' ? null : 'report'"
          >
            <FileText :size="16" />
            {{ t("governance.report") }} ({{ session.work.issues.length }})
          </button>
        </template>
        <details v-if="rounds.length" class="kg-history">
          <summary class="kg-button">{{ t("governance.rounds") }}</summary>
          <nav @click="closeHistory">
            <RouterLink
              v-for="round in rounds"
              :key="round.id"
              :to="sessionLocation(round.id)"
            >
              {{ new Date(round.createdAt).toLocaleString() }} ·
              {{ t(`governance.${round.status}`) }}
            </RouterLink>
          </nav>
        </details>
      </div>
    </header>
    <p v-if="error" role="alert" class="kg-error">{{ error }}</p>
    <p v-if="loading" role="status">{{ t("governance.loading") }}</p>
    <div v-else class="kg-body">
      <main class="kg-chat">
        <div class="kg-thread">
          <div
            ref="conversation"
            class="kg-messages"
            role="log"
            :aria-label="t('governance.discussion')"
          >
            <p v-if="!messages.length && !running" class="kg-notice">
              {{ t("governance.subtitle") }}
            </p>
            <div v-for="(message, index) in messages" :key="index" class="kg-message">
              <AgentMessage :message="message" :message-index="index" />
            </div>
            <p v-if="running" class="kg-working" role="status">{{ t("governance.working") }}</p>
            <template v-if="session && !running">
              <p v-if="session.status === 'stopped'" class="kg-notice">
                {{ t("governance.stoppedHint") }}
              </p>
              <p v-if="session.error" role="alert" class="kg-error">
                {{ t("governance.failedHint") }}
              </p>
            </template>
          </div>
          <p v-if="session" class="kg-status" aria-live="polite">
            <span :class="{ 'kg-pulse': running }">{{ statusText }}</span>
            <span v-if="progress">{{ progress }}</span>
            <button v-if="streamLost" type="button" class="kg-button" @click="reload">
              {{ t("common.retry") }}
            </button>
          </p>
          <div class="kg-composer">
            <div class="kg-quick">
              <template v-if="!session">
                <button
                  type="button"
                  :disabled="running || sending"
                  @click="submit(t('governance.startCheck'), false)"
                >
                  {{ t("governance.startCheck") }}
                </button>
              </template>
              <template v-else>
                <button
                  type="button"
                  :disabled="running || sending"
                  @click="submit(t('governance.explain'), false)"
                >
                  {{ t("governance.explain") }}
                </button>
                <button
                  type="button"
                  :disabled="running || sending"
                  @click="submit(t('governance.defer'), false)"
                >
                  {{ t("governance.defer") }}
                </button>
                <button
                  v-if="session.status === 'stopped' || session.status === 'failed'"
                  type="button"
                  :disabled="running || sending"
                  @click="submit(t('governance.continue'), false)"
                >
                  {{ t("governance.continue") }}
                </button>
              </template>
            </div>
            <AgentChatInput
              v-model="input"
              :is-sending="running || sending"
              :placeholder="t('governance.placeholder')"
              :maxlength="20000"
              @send="submit(input, true)"
              @stop="stop"
            />
            <small>{{ t("governance.saved") }}</small>
          </div>
        </div>
      </main>
      <aside
        v-if="session && panel"
        ref="panelElement"
        class="kg-panel"
        :aria-label="t(`governance.${panel}`)"
      >
      <header>
          <h2>{{ t(`governance.${panel}`) }}</h2>
          <button class="kg-button" @click="panel = null">{{ t("governance.close") }}</button>
        </header>
        <template v-if="panel === 'report'">
          <p class="kg-notice">{{ t("governance.reportHint") }}</p>
          <p v-if="session.work.summary" class="kg-summary">{{ session.work.summary }}</p>
          <p v-if="!session.work.issues.length">{{ t("governance.noIssues") }}</p>
          <article v-for="issue in session.work.issues" :key="issue.id" class="kg-issue">
            <div class="kg-tags">
              <span>{{ t(`governance.${issue.category}`) }}</span>
              <span>{{ t(`governance.${issue.status}`) }}</span>
            </div>
            <h3>{{ issue.title }}</h3>
            <dl>
              <dt>{{ t("governance.problem") }}</dt>
              <dd>{{ issue.problem }}</dd>
              <dt>{{ t("governance.before") }}</dt>
              <dd>{{ issue.before }}</dd>
              <dt>{{ t("governance.after") }}</dt>
              <dd>{{ issue.after }}</dd>
              <dt>{{ t("governance.basis") }}</dt>
              <dd>{{ issue.basis }}</dd>
            </dl>
            <button
              v-for="id in issue.sourceIds"
              :key="id"
              class="kg-source-link"
              @click="showSource(id)"
            >
              {{
                session.work.sources.find((item) => item.id === id)?.title ||
                t("governance.sources")
              }}
            </button>
          </article>
        </template>
        <template v-else>
          <p v-if="!session.work.sources.length">{{ t("governance.noSources") }}</p>
          <button
            v-for="item in session.work.sources"
            :key="item.id"
            class="kg-source-link"
            @click="readSource(item.id)"
          >
            {{ item.title }}
          </button>
          <section v-if="source" class="kg-original">
            <h3>{{ source.source.title }}</h3>
            <p v-if="!source.page">{{ t("governance.evidenceHint") }}</p>
            <p v-else-if="!source.page.items.length">{{ t("governance.sourceMissing") }}</p>
            <template v-else>
              <p v-for="item in source.page.items" :key="item.id" class="kg-original-text">
                {{ item.text }}
              </p>
              <button
                v-if="source.page.next"
                class="kg-button"
                @click="readSource(source.source.id, source.page.next.offset)"
              >
                {{ t("governance.more") }}
              </button>
            </template>
          </section>
        </template>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.kg-page {
  min-width: 0;
  color: var(--el-text-color-primary);
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  padding: 16px 24px;
  gap: 12px;
  box-sizing: border-box;
}
.kg-header,
.kg-heading,
.kg-toolbar,
.kg-status,
.kg-panel > header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kg-header,
.kg-panel > header {
  justify-content: space-between;
}
.kg-header,
.kg-toolbar,
.kg-status,
.kg-tags {
  flex-wrap: wrap;
}
.kg-header {
  position: relative;
  z-index: 2;
  gap: 12px;
}
.kg-heading {
  min-width: 0;
  flex: 1 1 160px;
}
.kg-toolbar {
  justify-content: flex-end;
  max-width: 100%;
}
h1 {
  font-size: 20px;
  margin: 0;
  min-width: 0;
  overflow-wrap: anywhere;
}
h2 {
  font-size: 18px;
}
h3 {
  font-size: 16px;
}
button,
summary {
  font: inherit;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
button:focus-visible,
summary:focus-visible,
a:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 3px;
}
.kg-button,
.kg-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px solid var(--el-border-color);
  border-radius: 7px;
  padding: 8px 12px;
  background: var(--el-bg-color);
  color: inherit;
  white-space: normal;
  max-width: 100%;
}
.kg-icon {
  padding: 8px;
}
.kg-button[aria-pressed="true"] {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
.kg-history summary {
  list-style: none;
}
.kg-history summary::-webkit-details-marker {
  display: none;
}
.kg-history nav {
  position: absolute;
  z-index: 3;
  top: calc(100% + 4px);
  inset-inline-end: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: min(320px, 100%);
  max-height: min(240px, 40vh);
  overflow: auto;
  padding: 6px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}
.kg-history a {
  color: var(--el-color-primary);
  overflow-wrap: anywhere;
  padding: 6px 4px;
}
.kg-status {
  margin: 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.kg-pulse {
  color: var(--el-color-primary);
}
.kg-summary {
  font-size: 13px;
  margin: 8px 0 0;
  padding: 10px 14px;
  line-height: 1.6;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
.kg-body {
  flex: 1;
  display: flex;
  min-height: 0;
  gap: 20px;
}
.kg-chat {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: center;
  min-height: 0;
}
.kg-thread {
  width: 100%;
  max-width: 760px;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.kg-messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 0;
}
.kg-message {
  padding: 8px 0;
  overflow-wrap: anywhere;
}
.kg-working,
.kg-notice,
.kg-composer small {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.kg-error {
  font-size: 13px;
  color: var(--el-color-danger);
  overflow-wrap: anywhere;
}
.kg-composer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}
.kg-composer :deep(.chat-input-wrapper) {
  padding: 0;
  border-top: 0;
}
.kg-quick {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.kg-quick button {
  max-width: 100%;
  font-size: 12px;
  color: var(--el-text-color-regular);
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
  border-radius: 16px;
  padding: 5px 10px;
}
.kg-panel {
  width: 390px;
  max-width: 45%;
  min-width: 0;
  overflow-y: auto;
  padding: 0 16px;
  border-left: 1px solid var(--el-border-color-lighter);
}
.kg-panel > header {
  position: sticky;
  top: 0;
  background: var(--el-bg-color);
  z-index: 1;
}
.kg-issue {
  padding: 18px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  overflow-wrap: anywhere;
}
.kg-tags {
  display: flex;
  gap: 8px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.kg-tags span {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  padding: 3px 6px;
}
dt {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-top: 12px;
}
dd {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.kg-source-link {
  display: block;
  text-align: left;
  color: var(--el-color-primary);
  background: none;
  border: 0;
  padding: 7px 0;
  font-size: 12px;
  overflow-wrap: anywhere;
}
.kg-original-text {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font-size: 12px;
  line-height: 1.65;
}
@media (max-width: 800px) {
  .kg-page {
    padding: 12px;
  }
  .kg-header {
    align-items: flex-start;
  }
  .kg-body {
    flex-direction: column;
    overflow-y: auto;
  }
  .kg-chat {
    flex: 1 0 400px;
  }
  .kg-panel {
    width: auto;
    max-width: none;
    border-left: 0;
    border-top: 1px solid var(--el-border-color);
    padding: 0 8px;
    overflow: visible;
  }
  .kg-panel > header {
    position: static;
  }
}
@media (prefers-reduced-motion: reduce) {
  * {
    scroll-behavior: auto;
  }
}
</style>

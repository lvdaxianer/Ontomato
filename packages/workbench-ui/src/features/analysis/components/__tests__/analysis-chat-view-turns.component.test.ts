import { beforeEach, describe, expect, it, vi } from "vitest";
import { nextTick } from "vue";
import { flushPromises, mount } from "@vue/test-utils";
import { createI18n } from "vue-i18n";
import ElementPlus from "element-plus";
import { createPinia, setActivePinia } from "pinia";
import AnalysisChatView from "../AnalysisChatView.vue";
import { useAnalysisStore, type TaskSession } from "../../stores/analysis";
import en from "../../../../locales/en";
import type { AnalysisAgent } from "@ontomato/contracts/analysis-agent";
import type { EgressMessage } from "@ontomato/contracts/agent-egress";
import type {
  AnalysisTask,
  AnalysisTaskRun,
} from "@ontomato/contracts/analysis-task";
import type {
  AnalysisActivity,
  AnalysisRunState,
  DeepAnalysisTaskPayload,
} from "@ontomato/contracts/analysis-presentation";
import type { Message, ResponseSnapshot } from "../../../../types/chat";
import { turnDiagnosisAccessKey } from "../../../diagnosis/access";

const getRunSnapshot = vi.fn();
const getLoopSubagentTrace = vi.fn();
const getQuestionExecution = vi.fn(async (..._args: unknown[]) => ({}));

vi.mock("../../api", () => ({
  analysisAgentApi: {},
  analysisTaskApi: {
    getLoopSubagentTrace: (...args: unknown[]) => getLoopSubagentTrace(...args),
    getRunSnapshot: (...args: unknown[]) => getRunSnapshot(...args),
    getQuestionExecution: (...args: unknown[]) => getQuestionExecution(...args),
  },
  analysisReportApi: {},
  streamTaskExecution: vi.fn(() => () => {}),
  streamFollowUp: vi.fn(() => () => {}),
  streamFollowUpRun: vi.fn(() => () => {}),
  cancelDeepAnalysisRun: vi.fn(),
  cancelFollowUpRun: vi.fn(async () => ({ ok: true })),
}));

function activity(narrative: string): AnalysisActivity {
  return {
    activityId: `activity-${narrative}`,
    seq: 1,
    revision: 1,
    kind: "narrative",
    status: "completed",
    startedAt: 1,
    narrative,
  };
}

function runState(status: AnalysisRunState["status"], done: number): AnalysisRunState {
  return { status, revision: 1, progress: { done, label: "" } };
}

function execution(
  activities: AnalysisActivity[],
  patch: Partial<DeepAnalysisTaskPayload> = {}
): DeepAnalysisTaskPayload {
  return {
    executionMode: "loop",
    runState: runState("completed", activities.length),
    activities,
    sections: [],
    charts: [],
    chartDiagnostics: [],
    ...patch,
  };
}

function answerMessage(
  id: number,
  answer: string,
  narrative: string,
  patch: Partial<DeepAnalysisTaskPayload> = {},
  trace: EgressMessage[] = []
): Message {
  return {
    id,
    role: "assistant",
    content: answer,
    snapshot: {
      mode: "deep-analysis",
      status: "completed",
      source: "history",
      primaryText: answer,
      trace,
      deepAnalysis: execution([activity(narrative)], { finalAnswer: answer, ...patch }),
    },
  };
}

function agent(reportDeliverableEnabled: boolean): AnalysisAgent {
  return {
    id: "agent-1",
    name: "Harness",
    description: "",
    executionMode: "loop",
    graphScope: { mode: "panorama" },
    reportDeliverableEnabled,
    summarizerPrompt: "",
    conclusionMakerPrompt: "",
    isEnabled: true,
    createdAt: 1,
    updatedAt: 1,
  };
}

function taskFixture(patch: Partial<AnalysisTask> = {}): AnalysisTask {
  return {
    id: "task-1",
    agentId: "agent-1",
    userId: "owner",
    name: "conversation-task",
    description: "",
    status: "completed",
    reportDeliverableEnabled: false,
    question: "task-question",
    scheduleEnabled: false,
    notifyOnComplete: false,
    triggerSource: "manual",
    createdAt: 1,
    updatedAt: 1,
    ...patch,
  };
}

function sessionFixture(
  messages: Message[],
  streamingSnapshot: ResponseSnapshot | null = null
): TaskSession {
  return {
    taskId: "task-1",
    agentId: "agent-1",
    threadId: "thread-1",
    taskName: "conversation-task",
    messages,
    input: "",
    isLoading: false,
    streamingSnapshot,
    stopRequested: false,
  };
}

function seed(options: {
  messages: Message[];
  streamingSnapshot?: ResponseSnapshot | null;
  report?: boolean;
  taskPatch?: Partial<AnalysisTask>;
  runs?: AnalysisTaskRun[];
  runIndex?: number;
}) {
  const store = useAnalysisStore();
  const report = options.report ?? false;
  store.agents = [agent(report)];
  store.selectedAgentId = "agent-1";
  store.tasks = [taskFixture({ reportDeliverableEnabled: report, ...options.taskPatch })];
  store.currentTaskId = "task-1";
  store.taskRuns = options.runs ?? [];
  store.taskRunIndex = options.runIndex ?? 0;
  store.sessions = {
    "task-1": sessionFixture(options.messages, options.streamingSnapshot ?? null),
  };
}

function streamingExecution(activities: AnalysisActivity[]): ResponseSnapshot {
  return {
    mode: "deep-analysis",
    status: "streaming",
    source: "live",
    primaryText: "",
    deepAnalysis: execution(activities, { runState: runState("running", 0) }),
  };
}

function render(diagnosisVisible = false) {
  return mount(AnalysisChatView, {
    global: {
      plugins: [createI18n({ legacy: false, locale: "en", messages: { en } }), ElementPlus],
      stubs: { DeepAnalysisResult: true, QueryExecutionView: true },
      // The turn diagnosis entry is injected by the app; here permissions are not loaded, so the entry is hidden.
      provide: {
        [turnDiagnosisAccessKey as symbol]: { prepare: () => {}, visible: () => diagnosisVisible },
      },
    },
  });
}

/** Opens every collapsed work process, as a user reviewing finished turns would. */
async function openProcesses(wrapper: ReturnType<typeof render>) {
  for (const header of wrapper.findAll('.process-header[aria-expanded="false"]'))
    await header.trigger("click");
}

function occurrences(text: string, value: string): number {
  return text.split(value).length - 1;
}

function userTrace(text: string): EgressMessage {
  return { role: "user", content: [{ type: "text", text }] };
}

function toolCallTrace(name: string, args: Record<string, unknown>): EgressMessage {
  return {
    role: "assistant",
    content: [{ type: "toolCall", id: `call-${name}`, name, arguments: args }],
  };
}

function toolResultTrace(toolName: string, text: string): EgressMessage {
  return {
    role: "toolResult",
    toolCallId: `call-${toolName}`,
    toolName,
    content: [{ type: "text", text }],
  };
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.clearAllMocks();
});

// Public surface under test: first-turn layout; observable result: the request is followed by its work process and reply,
// with no fixed acknowledgement standing in for the agent.
it("follows the user's question with the turn's work process, without a fixed acknowledgement", () => {
  seed({
    messages: [
      { id: 1, role: "user", content: "user-analysis-question" },
      answerMessage(2, "analysis-answer", "analysis-activity"),
    ],
  });

  const wrapper = render();
  const children = wrapper.find(".messages-inner").element.children;
  expect(children[0].classList).toContain("user");
  expect(children[0].textContent).toContain("user-analysis-question");
  expect(children[1].classList).toContain("turn-process");
  expect(children[2].textContent).toContain("analysis-answer");
  expect(wrapper.text()).not.toContain("deep analysis");
});

it("shows the run switcher below the user's question", () => {
  seed({
    report: true,
    messages: [
      { id: 1, role: "user", content: "user-analysis-question" },
      answerMessage(2, "analysis-answer", "analysis-activity"),
    ],
    runs: [
      { id: "run-1", status: "completed" },
      { id: "run-2", status: "completed" },
    ],
    runIndex: 1,
  });

  const children = render().find(".messages-inner").element.children;
  expect(children[0].classList).toContain("user");
  expect(children[1].classList).toContain("run-switcher-row");
  expect(children[2].classList).toContain("turn-process");
});

// Public surface under test: execution output consumed once per turn; observable result: both Q&A turns and both activities are present, with no repeated replies.
it("renders every turn's question, activities and reply instead of only the last turn", async () => {
  seed({
    messages: [
      { id: 1, role: "user", content: "first-question" },
      answerMessage(2, "first-answer", "first-round-writing", {
        activities: [
          activity("first-round-writing"),
          {
            activityId: "activity-evidence",
            seq: 2,
            revision: 1,
            kind: "evidence",
            status: "completed",
            startedAt: 1,
            questions: [
              {
                questionId: "q-1",
                question: "data-query-question",
                status: "completed",
                execution: {},
              },
            ],
          },
        ],
      }),
      { id: 3, role: "user", content: "second-question" },
      answerMessage(4, "second-answer", "second-round-writing"),
    ],
  });
  const wrapper = render();
  await openProcesses(wrapper);
  const text = wrapper.text();

  expect(text).toContain("first-question");
  expect(text).toContain("second-question");
  expect(text).toContain("first-round-writing");
  expect(text).toContain("second-round-writing");
  // Once Node fills in question.execution in /turns, the existing QueryExecutionView picks up that execution fact on demand.
  expect(wrapper.find("query-execution-view-stub").exists()).toBe(false);
  for (const row of wrapper.findAll("button.process-row:not([disabled])")) await row.trigger("click");
  await wrapper.find(".question-toggle").trigger("click");
  expect(wrapper.find("query-execution-view-stub").exists()).toBe(true);
  expect(occurrences(text, "first-answer")).toBe(1);
  expect(occurrences(text, "second-answer")).toBe(1);
});

// Public surface under test: partial body of a cancelled turn and the streaming tail; observable result: the partial body stays without duplication and the streaming turn renders normally.
it("keeps a cancelled turn's partial answer while the next turn streams", async () => {
  seed({
    messages: [
      { id: 1, role: "user", content: "first-question" },
      answerMessage(2, "completed-partial-answer", "completed-round-writing", { runState: runState("cancelled", 1) }),
      { id: 3, role: "user", content: "second-question" },
    ],
    streamingSnapshot: streamingExecution([activity("in-progress-round-writing")]),
  });
  const wrapper = render();
  // The running turn shows its work without being opened.
  expect(wrapper.text()).toContain("in-progress-round-writing");
  expect(wrapper.text()).toContain("Execution stopped");
  await openProcesses(wrapper);
  const text = wrapper.text();

  expect(text).toContain("completed-partial-answer");
  expect(text).toContain("completed-round-writing");
  expect(text).toContain("in-progress-round-writing");
  expect(occurrences(text, "completed-partial-answer")).toBe(1);
});

// Public surface under test: streaming body of a standard follow-up; observable result: the body and typing tail appear in that turn.
it("shows the streaming carrier text for a standard follow-up turn", () => {
  seed({
    messages: [
      { id: 1, role: "user", content: "report-question" },
      answerMessage(2, "report-reply", "report-round-activity"),
      { id: 3, role: "user", content: "follow-up-question" },
    ],
    streamingSnapshot: {
      mode: "standard",
      status: "streaming",
      source: "live",
      primaryText: "follow-up-streaming-body",
    },
  });
  const wrapper = render();

  expect(wrapper.text()).toContain("follow-up-streaming-body");
  expect(wrapper.find(".typing-cursor").exists()).toBe(true);
});

// Public surface under test: the authoritative source of per-turn replies; observable result: a finished turn's answer is not carried into the unfinished new turn (task metadata carries no answer).
it("keeps a finished turn's answer out of the next unfinished turn", () => {
  seed({
    messages: [
      { id: 1, role: "user", content: "question-A" },
      answerMessage(2, "answer-A", "round-A-activity"),
      { id: 3, role: "user", content: "question-B" },
    ],
    streamingSnapshot: streamingExecution([activity("round-B-in-progress")]),
  });
  const text = render().text();

  expect(text).toContain("question-A");
  expect(text).toContain("question-B");
  expect(text).toContain("round-B-in-progress");
  expect(occurrences(text, "answer-A")).toBe(1);
});

// Public surface under test: history run selection of report tasks; observable result: without a run snapshot the report body
// is not disguised as a chat message; the report view still renders the report body.
it("does not show a historical run's report body in the conversation view", () => {
  seed({
    report: true,
    messages: [
      { id: 1, role: "user", content: "current-question" },
      answerMessage(2, "current-answer", "current-round-activity"),
    ],
    runs: [
      { id: "run-1", status: "completed", resultReport: "first-run-body" },
      { id: "run-2", status: "completed", resultReport: "second-run-body" },
    ],
    runIndex: 0,
  });
  const text = render().text();

  expect(text).toContain("task-question");
  expect(text).not.toContain("first-run-body");
  expect(text).not.toContain("second-run-body");
  // Run records have no public messages, so no empty "messages and tools" panel should appear.
  expect(render().findAll(".message-trace-list")).toHaveLength(0);
});

// Public surface under test: per-turn public messages and tool results; observable result: expanding does not mix turns and internal thinking fields are never read.
it("does not leak partial report text from a cancelled run into chat", async () => {
  const cancelledPayload = execution([activity("pre-cancel-activity")], {
    runState: runState("cancelled", 1),
    sections: [
      {
        sectionId: "report",
        order: 0,
        revision: 1,
        markdown: "pre-cancel-partial-report",
        status: "success",
      },
    ],
  });
  // Run lists carry no replay snapshot; selecting the old run reads its own.
  getRunSnapshot.mockResolvedValue({
    mode: "deep-analysis",
    status: "failed",
    source: "history",
    primaryText: "pre-cancel-partial-report",
    deepAnalysis: cancelledPayload,
  });
  seed({
    report: true,
    messages: [],
    runs: [
      { id: "run-cancelled", status: "cancelled", resultReport: "pre-cancel-partial-report" },
      { id: "run-current", status: "completed", resultReport: "current-report" },
    ],
    runIndex: 1,
  });
  await useAnalysisStore().selectRun(0);
  expect(getRunSnapshot).toHaveBeenCalledWith("task-1", "run-cancelled");

  const wrapper = render();
  await openProcesses(wrapper);
  const text = wrapper.text();
  expect(text).toContain("pre-cancel-activity");
  expect(text).not.toContain("pre-cancel-partial-report");
});

it("keeps each turn's messages and tool results inside its own expandable trace", async () => {
  seed({
    messages: [
      { id: 1, role: "user", content: "first-question" },
      answerMessage(2, "first-answer", "first-round-writing", {}, [
        userTrace("first-question"),
        userTrace("[Orders](https://example.com/orders) submitted"),
        userTrace('{"sku":"SKU42","qty":3}'),
        userTrace('<img src=x onerror="alert(1)">'),
        toolCallTrace("orders_query", { region: "north" }),
        toolResultTrace("orders_query", "orders-result-A"),
        { ...userTrace("hidden-message"), thinking: "internal-thinking-A" } as unknown as EgressMessage,
      ]),
      { id: 3, role: "user", content: "second-question" },
      answerMessage(4, "second-answer", "second-round-writing", {}, [
        toolCallTrace("stock_query", { sku: "SKU42" }),
        toolResultTrace("stock_query", "stock-result-B"),
      ]),
    ],
  });
  const wrapper = render();
  const toggles = wrapper.findAll(".turn-trace-toggle");

  expect(toggles).toHaveLength(2);
  expect(wrapper.text()).not.toContain("orders-result-A");
  expect(wrapper.text()).not.toContain("internal-thinking-A");

  await toggles[0].trigger("click");
  expect(wrapper.text()).toContain("orders_query");
  expect(wrapper.text()).toContain("orders-result-A");
  expect(wrapper.text()).not.toContain("stock_query");
  expect(wrapper.text()).not.toContain("stock-result-B");
  expect(wrapper.text()).not.toContain("internal-thinking-A");
  // Per-turn public messages reuse the old safe renderer: Markdown links and JSON code blocks are kept and raw HTML is not executed.
  expect(wrapper.find('.loop-trace-text a[href="https://example.com/orders"]').exists()).toBe(true);
  const codeBlock = wrapper.find(".loop-trace-text pre code");
  expect(codeBlock.exists()).toBe(true);
  expect(codeBlock.text()).toContain('"sku"');
  expect(wrapper.find(".loop-trace-text img").exists()).toBe(false);
  expect(wrapper.text()).toContain("onerror");

  await toggles[1].trigger("click");
  expect(wrapper.text()).toContain("stock_query");
  expect(wrapper.text()).toContain("stock-result-B");
  expect(occurrences(wrapper.text(), "orders-result-A")).toBe(1);
  expect(occurrences(wrapper.text(), "stock-result-B")).toBe(1);
});

// Public surface under test: a regular continuous session does not switch by run history; observable result: all turns and activities stay present,
// the unfinished turn's streaming tail is still visible, and the "can continue" flag the store gives the input is true.
it("keeps every turn visible for a regular conversation with run history", async () => {
  seed({
    messages: [
      { id: 1, role: "user", content: "first-question" },
      answerMessage(2, "first-answer", "first-round-writing"),
      { id: 3, role: "user", content: "second-question" },
      answerMessage(4, "second-answer", "second-round-writing"),
      { id: 5, role: "user", content: "third-question" },
    ],
    streamingSnapshot: streamingExecution([activity("third-round-in-progress")]),
    runs: [
      { id: "run-1", status: "completed", resultReport: "first-run-body" },
      { id: "run-2", status: "completed", resultReport: "second-run-body" },
    ],
    runIndex: 0,
  });
  const store = useAnalysisStore();
  const wrapper = render();
  await openProcesses(wrapper);
  const text = wrapper.text();

  expect(text).toContain("first-question");
  expect(text).toContain("second-question");
  expect(text).toContain("first-round-writing");
  expect(text).toContain("second-round-writing");
  expect(text).toContain("third-question");
  expect(text).toContain("third-round-in-progress");
  expect(store.isSelectedLatestRun).toBe(true);
  expect(store.displayStreamingSnapshot?.mode).toBe("deep-analysis");
  store.sessions["task-1"].isLoading = true;
  expect(store.displayIsLoading).toBe(true);
  expect(text).not.toContain("first-run-body");
  expect(wrapper.find(".typing-cursor, .streaming-dots").exists()).toBe(false);
});

// Public surface under test: one report-task execution plus a standard follow-up; observable result: the report body stays in the report view and the follow-up renders as-is.
it("keeps one execution block plus standard follow-up turns for a report task", async () => {
  seed({
    report: true,
    messages: [
      { id: 1, role: "user", content: "report-question" },
      answerMessage(2, "report-body-ABC", "report-round-activity", {
        sections: [{ sectionId: "s1", order: 0, revision: 1, markdown: "report-body-ABC" }],
      }),
      { id: 3, role: "user", content: "follow-up-question" },
      {
        id: 4,
        role: "assistant",
        content: "follow-up-answer",
        snapshot: {
          mode: "standard",
          status: "completed",
          source: "history",
          primaryText: "follow-up-answer",
        },
      },
    ],
  });
  const wrapper = render();
  await openProcesses(wrapper);
  const text = wrapper.text();

  expect(text).toContain("report-question");
  expect(text).toContain("report-round-activity");
  expect(wrapper.find(".follow-up-divider").exists()).toBe(true);
  expect(text).toContain("follow-up-question");
  expect(text).toContain("follow-up-answer");
  expect(text).not.toContain("report-body-ABC");
});

// The turn closes with how it ended; the turn's diagnosis entry follows as a secondary tool.
it("ends a failed turn with its failure and reason, then this turn's diagnosis entry", () => {
  const answer = answerMessage(2, "partial-answer", "closing-activity", {
    runState: { ...runState("failed", 1), error: "model-timeout" },
  });
  answer.snapshot = { ...answer.snapshot!, threadId: "thread-1", requestSeq: 4 };
  seed({ messages: [{ id: 1, role: "user", content: "question" }, answer] });

  const wrapper = render(true);
  const outcome = wrapper.find(".turn-outcome");
  expect(outcome.text()).toContain("Execution failed");
  expect(outcome.text()).toContain("model-timeout");
  const html = wrapper.html();
  expect(html.indexOf("turn-diagnose-btn")).toBeGreaterThan(html.indexOf("turn-outcome"));
});

// ---- Work process across the three agent combinations ----

type Combination = "regular-loop" | "report-loop" | "report-dimension";
type RunStatus = AnalysisRunState["status"];

function loopStep(patch: Partial<AnalysisActivity> & Pick<AnalysisActivity, "activityId" | "kind">): AnalysisActivity {
  return { seq: 1, revision: 1, status: "completed", startedAt: 1, ...patch };
}

function dimensionActivities(): AnalysisActivity[] {
  return [
    loopStep({
      activityId: "plan",
      kind: "plan",
      dimensions: [
        {
          dimensionId: "d1",
          name: "region-dimension",
          questions: [
            { questionId: "q1", question: "region-sales" },
            { questionId: "q2", question: "region-growth" },
          ],
        },
        {
          dimensionId: "d2",
          name: "channel-dimension",
          questions: [{ questionId: "q3", question: "channel-share" }],
        },
      ],
    }),
    loopStep({
      activityId: "evidence:d1",
      kind: "evidence",
      groupId: "d1",
      status: "running",
      questions: [
        { questionId: "q1", question: "region-sales", status: "completed", dataCount: 3 },
        { questionId: "q2", question: "region-growth", status: "running" },
      ],
    }),
  ];
}

function payloadFor(
  combination: Combination,
  status: RunStatus,
  patch: Partial<DeepAnalysisTaskPayload> = {}
): DeepAnalysisTaskPayload {
  const dimension = combination === "report-dimension";
  return {
    executionMode: dimension ? "dimension" : "loop",
    runState: { ...runState(status, 1), error: status === "failed" ? "upstream-error" : undefined },
    activities: dimension ? dimensionActivities() : [activity("loop-narrative")],
    // Completed runs carry a report body, so only the delivery mode decides whether it is offered.
    sections:
      status === "completed"
        ? [{ sectionId: "s1", order: 0, revision: 1, markdown: "report-body", status: "success" }]
        : [],
    charts: [],
    chartDiagnostics: [],
    ...patch,
  };
}

function executionTurn(id: number, payload: DeepAnalysisTaskPayload, requestSeq?: number): Message {
  return {
    id,
    role: "assistant",
    content: "",
    snapshot: {
      mode: "deep-analysis",
      status: "completed",
      source: "history",
      primaryText: "",
      requestSeq,
      deepAnalysis: payload,
    },
  };
}

function seedTurn(combination: Combination, payload: DeepAnalysisTaskPayload) {
  const running = payload.runState.status === "running";
  seed({
    report: combination !== "regular-loop",
    messages: [
      { id: 1, role: "user", content: "question" },
      ...(running ? [] : [executionTurn(2, payload)]),
    ],
    streamingSnapshot: running
      ? { mode: "deep-analysis", status: "streaming", source: "live", primaryText: "", deepAnalysis: payload }
      : null,
  });
}

const STATE_TEXT: Record<RunStatus, string> = {
  running: "In progress",
  completed: "Completed",
  failed: "Failed",
  cancelled: "Stopped",
};

describe.each<Combination>(["regular-loop", "report-loop", "report-dimension"])(
  "%s turn",
  (combination) => {
    it.each<RunStatus>(["running", "completed", "failed", "cancelled"])(
      "shows a %s run with its own state and ending",
      (status) => {
        seedTurn(combination, payloadFor(combination, status));
        const wrapper = render();

        expect(wrapper.find(".process-state").text()).toBe(STATE_TEXT[status]);
        // Only a completed run carries a success mark.
        expect(wrapper.find(".process-header .tone-icon.tone-completed").exists()).toBe(
          status === "completed"
        );
        const outcome = wrapper.find(".turn-outcome");
        if (status === "failed") {
          expect(outcome.text()).toContain("Execution failed");
          expect(outcome.text()).toContain("upstream-error");
        } else if (status === "cancelled") {
          expect(outcome.text()).toContain("Execution stopped");
        } else if (status === "completed" && combination !== "regular-loop") {
          expect(outcome.find(".outcome-action").text()).toBe("View report");
        } else {
          expect(outcome.exists()).toBe(false);
        }
        expect(wrapper.find(".outcome-action").exists()).toBe(
          status === "completed" && combination !== "regular-loop"
        );
      }
    );
  }
);

it("offers the report only when the report view has content, and asks the host to open it", async () => {
  seedTurn("report-loop", payloadFor("report-loop", "completed", { sections: [] }));
  expect(render().find(".turn-outcome").exists()).toBe(false);

  seedTurn("report-loop", payloadFor("report-loop", "completed"));
  const wrapper = render();
  await wrapper.find(".outcome-action").trigger("click");
  expect(wrapper.emitted("openReport")).toHaveLength(1);
});

it("keeps a completed run successful while counting and explaining its failed parts", async () => {
  seedTurn(
    "regular-loop",
    payloadFor("regular-loop", "completed", {
      activities: [
        loopStep({
          activityId: "evidence",
          kind: "evidence",
          questions: [
            { questionId: "q-ok", question: "orders", status: "completed", dataCount: 2 },
            { questionId: "q-bad", question: "refunds", status: "failed", error: "column-missing" },
          ],
        }),
        loopStep({ activityId: "chart", kind: "chart", status: "failed", error: "render-failed" }),
      ],
    })
  );
  const wrapper = render();
  const header = wrapper.find(".process-header");

  expect(wrapper.find(".process-state").text()).toBe("Completed");
  expect(header.text()).toContain("Failed queries: 1");
  expect(header.text()).toContain("Failed activities: 1");
  expect(wrapper.find(".turn-outcome").exists()).toBe(false);

  await header.trigger("click");
  expect(wrapper.text()).toContain("render-failed");
  await wrapper.find("button.process-row:not([disabled])").trigger("click");
  expect(wrapper.text()).toContain("column-missing");
});

it("keeps the user's dimension expansion when the run completes", async () => {
  const running = payloadFor("report-dimension", "running");
  seedTurn("report-dimension", running);
  const wrapper = render();

  // The running dimension is open by default; the pending one is opened by the user.
  expect(wrapper.text()).toContain("region-growth");
  expect(wrapper.text()).not.toContain("channel-share");
  const channel = wrapper.findAll(".dimension-row")[1];
  await channel.trigger("click");
  expect(wrapper.text()).toContain("channel-share");

  const session = useAnalysisStore().sessions["task-1"];
  session.streamingSnapshot = null;
  session.messages = [
    ...session.messages,
    executionTurn(2, { ...payloadFor("report-dimension", "completed"), activities: running.activities }),
  ];
  await nextTick();

  expect(wrapper.find(".process-state").text()).toBe("Completed");
  expect(wrapper.find(".process-header").attributes("aria-expanded")).toBe("true");
  expect(wrapper.text()).toContain("channel-share");
});

it("keeps raw tool facts out of the first level and shows them on demand", async () => {
  seedTurn(
    "regular-loop",
    payloadFor("regular-loop", "running", {
      activities: [
        loopStep({
          activityId: "probe",
          kind: "probe",
          probeTool: "probe_class_data",
          probeArguments: { className: "OrdersClass" },
        }),
      ],
    })
  );
  const wrapper = render();

  expect(wrapper.text()).toContain("Data Probe");
  expect(wrapper.text()).not.toContain("probe_class_data");
  expect(wrapper.text()).not.toContain("OrdersClass");
  await wrapper.find("button.process-row").trigger("click");
  expect(wrapper.text()).toContain("probe_class_data");
  expect(wrapper.find(".detail-field pre").text()).toContain('"className": "OrdersClass"');
});

it("reads a regular turn's deferred query facts from that turn's own request", async () => {
  const deferredTurn = (question: string) =>
    payloadFor("regular-loop", "completed", {
      activities: [
        loopStep({
          activityId: `evidence-${question}`,
          kind: "evidence",
          questions: [
            { questionId: question, question, status: "completed", execution: {}, executionDeferred: true },
          ],
        }),
      ],
    });
  seed({
    messages: [
      { id: 1, role: "user", content: "first" },
      executionTurn(2, deferredTurn("q-first"), 3),
      { id: 3, role: "user", content: "second" },
      executionTurn(4, deferredTurn("q-second"), 5),
    ],
  });
  const wrapper = render();
  await openProcesses(wrapper);
  const rows = wrapper.findAll("button.process-row:not([disabled])");
  await rows[1].trigger("click");
  await wrapper.find(".question-toggle").trigger("click");

  expect(getQuestionExecution).toHaveBeenCalledTimes(1);
  expect(getQuestionExecution).toHaveBeenCalledWith("task-1", "q-second", {
    runId: undefined,
    requestSeq: 5,
  });
});

// ---- Turn identity across history rebuilds and run switching ----

function probeTurn(status: RunStatus): DeepAnalysisTaskPayload {
  return payloadFor("regular-loop", status, {
    activities: [
      loopStep({
        activityId: "probe",
        kind: "probe",
        probeTool: "probe_class_data",
        probeArguments: { className: "OrdersClass" },
      }),
    ],
  });
}

it("keeps the user's expansion when a live turn settles into history with rebuilt message ids", async () => {
  seed({
    messages: [{ id: 1_700_000_000_000, role: "user", content: "question" }],
    streamingSnapshot: {
      mode: "deep-analysis",
      status: "streaming",
      source: "live",
      primaryText: "",
      requestSeq: 7,
      deepAnalysis: probeTurn("running"),
    },
  });
  const wrapper = render();
  await wrapper.find("button.process-row").trigger("click");
  expect(wrapper.text()).toContain("OrdersClass");

  // Turn history replaces the live ids with 1-based ids, and a later reload rebuilds them again.
  const session = useAnalysisStore().sessions["task-1"];
  for (const ids of [
    [1, 2],
    [1_800_000_000_000, 1_800_000_000_001],
  ]) {
    session.streamingSnapshot = null;
    session.messages = [
      { id: ids[0], role: "user", content: "question" },
      executionTurn(ids[1], probeTurn("completed"), 7),
    ];
    await nextTick();

    expect(wrapper.find(".process-state").text()).toBe("Completed");
    expect(wrapper.find(".process-header").attributes("aria-expanded")).toBe("true");
    expect(wrapper.text()).toContain("OrdersClass");
  }
});

it("does not carry details or worker traces between historical runs that reuse ids", async () => {
  const dispatchRun = (runId: string) =>
    payloadFor("report-loop", "completed", {
      activities: [
        loopStep({ activityId: "dispatch-1", kind: "dispatch", topic: `topic-${runId}` }),
      ],
    });
  getRunSnapshot.mockImplementation(async (_taskId: string, runId: string) => ({
    mode: "deep-analysis",
    status: "completed",
    source: "history",
    primaryText: "",
    deepAnalysis: dispatchRun(runId),
  }));
  getLoopSubagentTrace.mockResolvedValue({
    messages: [{ role: "assistant", content: [{ type: "text", text: "worker-trace-text" }] }],
  });
  seed({
    report: true,
    // The current conversation reuses the historical view's message ids and activity ids.
    messages: [
      { id: 1, role: "user", content: "question" },
      executionTurn(2, dispatchRun("run-latest"), 2),
    ],
    runs: [
      { id: "run-a", status: "completed", requestSeq: 1 },
      { id: "run-b", status: "completed", requestSeq: 1 },
      { id: "run-latest", status: "completed", requestSeq: 2 },
    ],
    runIndex: 2,
  });
  const store = useAnalysisStore();
  await store.selectRun(0);
  const wrapper = render();
  await openProcesses(wrapper);
  await wrapper.find("button.process-row").trigger("click");
  await wrapper.find(".row-details .link-toggle").trigger("click");
  await flushPromises();
  expect(wrapper.text()).toContain("topic-run-a");
  expect(wrapper.text()).toContain("worker-trace-text");

  await store.selectRun(1);
  await flushPromises();
  expect(wrapper.text()).not.toContain("topic-run-a");
  expect(wrapper.find(".process-header").attributes("aria-expanded")).toBe("false");
  await openProcesses(wrapper);
  expect(wrapper.text()).toContain("topic-run-b");
  expect(wrapper.find(".row-details").exists()).toBe(false);
  await wrapper.find("button.process-row").trigger("click");
  expect(wrapper.text()).not.toContain("worker-trace-text");
  await wrapper.find(".row-details .link-toggle").trigger("click");
  await flushPromises();
  expect(getLoopSubagentTrace).toHaveBeenCalledTimes(2);

  // Returning to the current run renders at once, with no snapshot gap in between.
  await store.selectRun(2);
  await nextTick();
  expect(wrapper.text()).not.toContain("topic-run-b");
  expect(wrapper.find(".process-header").attributes("aria-expanded")).toBe("false");
  await openProcesses(wrapper);
  expect(wrapper.text()).toContain("topic-run-latest");
  expect(wrapper.find(".row-details").exists()).toBe(false);
  expect(wrapper.text()).not.toContain("worker-trace-text");
});

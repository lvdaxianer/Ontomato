import type {
  AnalysisActivity,
  AnalysisEvidenceQuestion,
} from "@ontomato/contracts/analysis-presentation";
import { workbenchContent } from "../../../content";

type Translate = (key: string, params?: Record<string, unknown>) => string;

/** Narrative activities are the agent's own words and render as text, not as a titled step. */
export type LoopStepKind = Exclude<AnalysisActivity["kind"], "narrative">;

/** Step kinds are exhaustively aligned with their display text. */
const ACTIVITY_LABEL_KEYS: Record<LoopStepKind, string> = {
  plan: "analysis.frameworkGeneration",
  skill: "analysis.loopActivitySkill",
  evidence: "analysis.loopActivityEvidence",
  chapter: "analysis.loopActivityChapter",
  publish: "analysis.loopActivityPublish",
  chart: "analysis.loopActivityChart",
  dispatch: "analysis.loopActivityDispatch",
  probe: "analysis.loopActivityProbe",
  external_tool: "analysis.loopActivityExternalTool",
};

/** Step titles are localized by the shared contract kind. */
export function loopActivityLabel(kind: LoopStepKind, t: Translate): string {
  return t(ACTIVITY_LABEL_KEYS[kind]);
}

/**
 * First-level step summary: only business-readable facts the activity actually carries (question
 * and chart counts, the dispatch topic, the chapter, the skills used). Tool names and arguments
 * stay in the step details; the failure reason is shown separately.
 */
export function loopActivitySummary(activity: AnalysisActivity, t: Translate): string {
  if (activity.skills?.length)
    return activity.skills
      .map((s) => `${s.skillId}${s.entry ? ` · ${s.entry}` : ""}`)
      .join(workbenchContent().skillListSeparator);
  if (activity.questions?.length) {
    const failed = activity.questions.filter((q) => q.status === "failed").length;
    const count = t("analysis.loopQuestionCount", { n: activity.questions.length });
    return failed ? `${count} · ${t("analysis.processFailedQuestions", { n: failed })}` : count;
  }
  if (activity.kind === "dispatch") return activity.topic || "";
  if (activity.charts?.length) return t("analysis.loopChartCount", { n: activity.charts.length });
  if (activity.chapter) return activity.chapter;
  return "";
}

export interface LoopDetailField {
  label: string;
  value: string;
  /** Raw structured values render as preformatted text. */
  raw?: boolean;
}

function formatArguments(args: Record<string, unknown> | undefined): string {
  return args && Object.keys(args).length > 0 ? JSON.stringify(args, null, 2) : "";
}

/** Raw tool facts behind a probe or external tool step, read on demand from the step details. */
export function loopActivityDetailFields(activity: AnalysisActivity, t: Translate): LoopDetailField[] {
  const fields: LoopDetailField[] = [];
  if (activity.kind === "external_tool") {
    if (activity.serviceName)
      fields.push({ label: t("analysis.loopDetailService"), value: activity.serviceName });
    if (activity.toolName) fields.push({ label: t("analysis.loopDetailTool"), value: activity.toolName });
    const args = formatArguments(activity.toolArguments);
    if (args) fields.push({ label: t("analysis.loopDetailArguments"), value: args, raw: true });
  }
  if (activity.kind === "probe") {
    if (activity.probeTool) fields.push({ label: t("analysis.loopDetailTool"), value: activity.probeTool });
    const args = formatArguments(activity.probeArguments);
    if (args) fields.push({ label: t("analysis.loopDetailArguments"), value: args, raw: true });
  }
  return fields;
}

/** Query question status text: a single question's failure reason stays on that question, not escalated to an activity failure. */
export function loopQuestionStatusText(question: AnalysisEvidenceQuestion, t: Translate): string {
  // Failure reason and row count are both execution-fact fields, read from the same place.
  if (question.status === "failed") {
    return question.execution?.error || question.error || t("analysis.queryFailed");
  }
  if (question.status === "cancelled") return t("common.stopped");
  if (question.status === "pending") return t("analysis.queryPending");
  if (question.status === "running") return t("analysis.querying");
  return t("analysis.rowCountHint", {
    n: question.execution?.dataCount ?? question.dataCount ?? 0,
  });
}

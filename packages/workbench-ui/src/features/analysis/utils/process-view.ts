import type { AnalysisActivity } from "@ontomato/contracts/analysis-presentation";

/**
 * Shared status vocabulary of the conversation's work process, a superset of the contract's
 * activity and question statuses; only "completed" reads as success.
 */
export type ProcessTone = "running" | "completed" | "failed" | "cancelled" | "pending";

export interface ProcessIssueCounts {
  failedActivities: number;
  failedQuestions: number;
}

/**
 * Failures recorded inside one run: failed activities and failed query questions are counted
 * separately, because a single question's failure is not an activity failure and neither decides
 * the run's own terminal state.
 */
export function processIssueCounts(activities: readonly AnalysisActivity[]): ProcessIssueCounts {
  return {
    failedActivities: activities.filter((a) => a.status === "failed").length,
    failedQuestions: activities
      .flatMap((a) => a.questions || [])
      .filter((q) => q.status === "failed").length,
  };
}

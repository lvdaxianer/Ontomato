import type { AgentResponseEvent } from "@ontomato/contracts/agent-chat";
import { describe, expect, it } from "vitest";

import {
  parseAgentResponseEvent,
  readSseData,
  reduceAgentLiveTail,
  SseProtocolError,
  type AgentLiveTail,
} from "../agent-chat-stream";

const backendEventFixtures: {
  [Type in AgentResponseEvent["type"]]: Extract<
    AgentResponseEvent,
    { type: Type }
  >;
} = {
  response_unavailable: { type: "response_unavailable" },
  response_started: { type: "response_started", message: "question" },
  token: { type: "token", content: "answer" },
  thinking: { type: "thinking", content: "reason" },
  tool_start: {
    type: "tool_start",
    toolCallId: "call-1",
    tool: "read",
    args: { path: "sample" },
  },
  tool_end: {
    type: "tool_end",
    toolCallId: "call-1",
    tool: "read",
    result: { content: [] },
    isError: false,
  },
  response_end: { type: "response_end", status: "completed" },
  error: { type: "error", error: "failed", timestamp: 1 },
};

function reduce(events: AgentResponseEvent[]): AgentLiveTail {
  return events.reduce<AgentLiveTail>(reduceAgentLiveTail, { messages: [], status: "starting" });
}

describe("agent chat stream reducer", () => {
  it("accepts every backend stream event variant at the frontend boundary", () => {
    for (const event of Object.values(backendEventFixtures)) {
      expect(parseAgentResponseEvent(event)).toEqual(event);
    }
  });

  it("builds one live tail from replayed text, thinking, and tool events", () => {
    const initial: AgentLiveTail = { messages: [], status: "starting" };
    const events: AgentResponseEvent[] = [
      { type: "response_started", message: "inspect" },
      { type: "thinking", content: "checking" },
      { type: "token", content: "found " },
      { type: "token", content: "it" },
      { type: "tool_start", toolCallId: "call-1", tool: "subagent", args: {} },
      {
        type: "tool_end",
        toolCallId: "call-1",
        tool: "subagent",
        result: { status: "completed" },
        isError: false,
      },
      { type: "response_end", status: "completed" },
    ];

    const result = events.reduce(reduceAgentLiveTail, initial);

    expect(initial).toEqual({ messages: [], status: "starting" });
    expect(result).toEqual({
      status: "completed",
      messages: [
        { role: "user", content: "inspect" },
        {
          role: "assistant",
          content: "found it",
          segments: [
            { type: "thinking", content: "checking" },
            { type: "text", content: "found it" },
            {
              type: "tool_call",
              toolCallId: "call-1",
              tool: "subagent",
              args: "{}",
              result: { status: "completed" },
              isError: false,
              status: "done",
            },
          ],
        },
      ],
    });
  });

  it("treats a replayed response start as idempotent and applies terminal states", () => {
    const result = reduce([
      { type: "response_started", message: "same" },
      { type: "response_started", message: "same" },
      { type: "token", content: "partial" },
      { type: "response_end", status: "cancelled" },
    ]);

    expect(result.messages.filter((message) => message.role === "user")).toHaveLength(1);
    expect(result.status).toBe("cancelled");
  });

  it("strictly rejects malformed stream payloads", () => {
    expect(parseAgentResponseEvent({ type: "token", content: 1 })).toBeUndefined();
    expect(
      parseAgentResponseEvent({ type: "tool_start", toolCallId: "1", tool: "read", args: [] })
    ).toBeUndefined();
    expect(
      parseAgentResponseEvent({ type: "response_end", status: "interrupted" })
    ).toBeUndefined();
    expect(parseAgentResponseEvent({ type: "unknown" })).toBeUndefined();
    expect(parseAgentResponseEvent({ type: "response_unavailable" })).toEqual({
      type: "response_unavailable",
    });
  });

  it("clears an unavailable transient tail so durable history can replace it", () => {
    const result = reduce([
      { type: "response_started", message: "question" },
      { type: "token", content: "partial" },
      { type: "response_unavailable" },
    ]);

    expect(result).toEqual({ messages: [], status: "idle" });
  });

  it("reads business events past connection control events and rejects malformed data", async () => {
    const body = (text: string) => new Response(text);
    const seen: Array<[unknown, number | undefined]> = [];
    const settled = await readSseData(
      body(
        'event: open\ndata: {"ok":true}\n\n' +
          'event: heartbeat\ndata: {"ts":1}\n\n' +
          'id: 3\ndata: {"type":"token","content":"a"}\n\n' +
          'data: {"type":"response_end","status":"completed"}\n\n'
      ),
      (data, id) => {
        seen.push([data, id]);
        return (data as { type: string }).type === "response_end";
      },
      () => true
    );
    expect(settled).toBe(true);
    expect(seen).toEqual([
      [{ type: "token", content: "a" }, 3],
      [{ type: "response_end", status: "completed" }, undefined],
    ]);
    await expect(
      readSseData(body('id: 1\ndata: {"type":\n\n'), () => false, () => true)
    ).rejects.toBeInstanceOf(SseProtocolError);
  });
});

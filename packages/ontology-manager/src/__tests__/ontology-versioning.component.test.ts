import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, h, nextTick, reactive, type App } from "vue";
import OntologyManager from "../OntologyManager.vue";
import { englishMessages } from "../i18n";
import type { ManagerHostContext } from "../context";

vi.mock("@antv/g6", async () => ({ Graph: (await import("./graph-double")).GraphDouble }));
vi.mock("@antv/x6", async () => ({
  Graph: (await import("./graph-double")).GraphDouble,
  Node: class {},
  Edge: class {},
}));

const requests: string[] = [];
let apps: App[] = [];
let previewConflict = false;
let historyMode: "normal" | "empty" | "error" = "normal";

const firstRule = {
  classDef: [{ className: "Order", showName: "订单", classDesc: "", attrs: [{ name: "id", showName: "编号", type: "string" }] }],
  relationship_rule: {},
};
const currentRule = {
  classDef: [
    { className: "Order", showName: "销售订单", classDesc: "", attrs: [{ name: "id", showName: "编号", type: "string" }, { name: "status", showName: "状态", type: "string" }] },
  ],
  relationship_rule: {},
};

function responseFor(url: string) {
  const path = url.replace(/^.*\/data-query/, "");
  if (path === "/admin/ontology/revisions?limit=100&offset=0") {
    if (historyMode === "error") return { success: false, code: "ONTOLOGY_REVISION_INTERNAL", message: "history unavailable" };
    if (historyMode === "empty") return { success: true, data: { activeRevision: null, items: [] } };
    return {
      success: true,
      data: {
        activeRevision: 2,
        items: [
          { revision: 1, contentHash: "hash-1", source: "migration", actorId: "system", createdAt: "2026-10-01T00:00:00Z" },
          { revision: 2, contentHash: "hash-2", source: "manual", actorId: "alice", createdAt: "2026-10-02T00:00:00Z" },
        ],
      },
    };
  }
  if (path === "/admin/ontology/revisions/1") {
    return { success: true, data: { formatVersion: 1, revision: 1, contentHash: "hash-1", source: "migration", actorId: "system", createdAt: "2026-10-01T00:00:00Z", content: firstRule } };
  }
  if (path === "/admin/ontology/revisions/2") {
    return { success: true, data: { formatVersion: 1, revision: 2, contentHash: "hash-2", source: "manual", actorId: "alice", createdAt: "2026-10-02T00:00:00Z", content: currentRule } };
  }
  if (path === "/admin/ontology/revisions/preview") {
    return {
      success: true,
      data: {
        currentRevision: 2,
        currentHash: "hash-2",
        baseRevision: 1,
        baseHash: "hash-1",
        incomingHash: "incoming-hash",
        merged: currentRule,
        conflicts: previewConflict
          ? [{ path: "$.classDef[className=Order].showName", baseValue: "订单", currentValue: "销售订单", incomingValue: "销售订单（导入）" }]
          : [],
        canApply: !previewConflict,
      },
    };
  }
  if (path === "/admin/ontology/revisions/apply") {
    return { success: true, data: { formatVersion: 1, revision: 3, contentHash: "hash-3", source: "import", actorId: "alice", createdAt: "2026-10-03T00:00:00Z", content: currentRule } };
  }
  if (path === "/admin/ontology/revisions/restore") {
    return { success: true, data: { formatVersion: 1, revision: 3, contentHash: "hash-3", source: "restore", actorId: "alice", createdAt: "2026-10-03T00:00:00Z", content: firstRule } };
  }
  return { success: true, data: {} };
}

beforeEach(() => {
  requests.length = 0;
  apps = [];
  previewConflict = false;
  historyMode = "normal";
  vi.stubGlobal("fetch", async (url: string) => {
    requests.push(url);
    return new Response(JSON.stringify(responseFor(url)), { status: 200, headers: { "Content-Type": "application/json" } });
  });
});

afterEach(() => {
  apps.forEach((app) => app.unmount());
  vi.unstubAllGlobals();
  document.body.innerHTML = "";
});

const base: ManagerHostContext = {
  apiBase: "/manager-api",
  credential: { type: "token", token: "token" },
  locale: "en",
  messages: englishMessages,
  theme: "light",
  view: "/ontology-versions",
  dataFallbacks: { noBusinessDescription: "No description", unnamedAsset: "Unnamed asset" },
  presentation: {
    themeProperties: { light: {}, dark: {} },
    nodePalette: ["#000001", "#000002", "#000003"],
    edgeLabel: {},
    maxFitZoom: null,
    dataBrowserDescription: () => "",
    compactDialogs: false,
  },
  knowledgeTagValues: { general: "General", business: "Business", tech: "Tech" },
  embedded: false,
  knowledgeGovernanceUrl: null,
};

async function settle() {
  for (let index = 0; index < 8; index += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await nextTick();
  }
}

describe("ontology versioning workspace", () => {
  it("loads history and renders a first-versus-current structured diff", async () => {
    const host = reactive({ ...base });
    const app = createApp({
      render: () => h(OntologyManager, { ...host }),
    });
    const element = document.createElement("div");
    document.body.append(element);
    app.mount(element);
    apps.push(app);
    await settle();

    expect(element.querySelector(".revision-surface")).not.toBeNull();
    expect(element.textContent).toContain("Ontology Version History");
    expect(element.textContent).toContain("Revision 1");
    expect(element.textContent).toContain("Revision 2");
    expect(element.textContent).toContain("Order");
    expect(element.textContent).toContain("Changed only");
    expect(requests.filter((url) => url.includes("/admin/ontology/revisions"))).toEqual([
      "/manager-api/data-query/admin/ontology/revisions?limit=100&offset=0",
      "/manager-api/data-query/admin/ontology/revisions/1",
      "/manager-api/data-query/admin/ontology/revisions/2",
    ]);
  });

  it("validates an exported file, previews it, and applies a conflict-free import once", async () => {
    const host = reactive({ ...base });
    const app = createApp({ render: () => h(OntologyManager, { ...host }) });
    const element = document.createElement("div");
    document.body.append(element);
    app.mount(element);
    apps.push(app);
    await settle();

    const importButton = [...element.querySelectorAll("button")].find((button) => button.textContent?.includes("Import ontology"));
    importButton?.click();
    await settle();
    const input = element.querySelector<HTMLInputElement>(".file-input");
    expect(input).not.toBeNull();
    Object.defineProperty(input, "files", {
      configurable: true,
      value: [new File([JSON.stringify({ revision: 1, contentHash: "hash-1", content: firstRule })], "revision.json", { type: "application/json" })],
    });
    input?.dispatchEvent(new Event("change", { bubbles: true }));
    await settle();

    const previewButton = [...element.querySelectorAll("button")].find((button) => button.textContent?.includes("Preview import"));
    previewButton?.click();
    await settle();
    expect(element.textContent).toContain("No conflicts detected");
    const applyButton = [...element.querySelectorAll("button")].find((button) => button.textContent?.includes("Apply import"));
    expect(applyButton?.hasAttribute("disabled")).toBe(false);
    applyButton?.click();
    await settle();

    expect(requests.filter((url) => url.includes("/admin/ontology/revisions/"))).toEqual(
      expect.arrayContaining([
        "/manager-api/data-query/admin/ontology/revisions/preview",
        "/manager-api/data-query/admin/ontology/revisions/apply",
      ])
    );
  });

  it("renders empty and failed history states with a retry action", async () => {
    historyMode = "empty";
    const host = reactive({ ...base });
    const app = createApp({ render: () => h(OntologyManager, { ...host }) });
    const element = document.createElement("div");
    document.body.append(element);
    app.mount(element);
    apps.push(app);
    await settle();
    expect(element.textContent).toContain("No ontology revision history");

    app.unmount();
    apps = [];
    document.body.innerHTML = "";
    historyMode = "error";
    const retryApp = createApp({ render: () => h(OntologyManager, { ...host }) });
    const retryElement = document.createElement("div");
    document.body.append(retryElement);
    retryApp.mount(retryElement);
    apps.push(retryApp);
    await settle();
    expect(retryElement.textContent).toContain("history unavailable");
    historyMode = "normal";
    [...retryElement.querySelectorAll("button")].find((button) => button.textContent?.includes("Retry"))?.click();
    await settle();
    expect(retryElement.textContent).toContain("Revision 2");
  });

  it("blocks applying an import while a semantic conflict remains unresolved", async () => {
    previewConflict = true;
    const host = reactive({ ...base });
    const app = createApp({ render: () => h(OntologyManager, { ...host }) });
    const element = document.createElement("div");
    document.body.append(element);
    app.mount(element);
    apps.push(app);
    await settle();

    [...element.querySelectorAll("button")].find((button) => button.textContent?.includes("Import ontology"))?.click();
    await settle();
    const input = element.querySelector<HTMLInputElement>(".file-input");
    Object.defineProperty(input, "files", {
      configurable: true,
      value: [new File([JSON.stringify({ revision: 1, contentHash: "hash-1", content: firstRule })], "revision.json", { type: "application/json" })],
    });
    input?.dispatchEvent(new Event("change", { bubbles: true }));
    await settle();
    [...element.querySelectorAll("button")].find((button) => button.textContent?.includes("Preview import"))?.click();
    await settle();

    expect(element.textContent).toContain("Resolve 1 conflicts before applying.");
    const applyButton = [...element.querySelectorAll("button")].find((button) => button.textContent?.includes("Apply import"));
    expect(applyButton?.hasAttribute("disabled")).toBe(true);
    expect(requests).not.toContain("/manager-api/data-query/admin/ontology/revisions/apply");
  });

  it("requires confirmation before restoring a historical revision", async () => {
    const host = reactive({ ...base });
    const app = createApp({ render: () => h(OntologyManager, { ...host }) });
    const element = document.createElement("div");
    document.body.append(element);
    app.mount(element);
    apps.push(app);
    await settle();

    element.querySelector<HTMLElement>(".revision-row")?.click();
    await settle();
    element.querySelector<HTMLElement>(".active-revision-card button")?.click();
    await settle();
    expect(document.body.textContent).toContain("Restore revision 1?");
    [...document.body.querySelectorAll("button")].find((button) => button.textContent?.trim() === "OK")?.click();
    await settle();
    expect(requests).toContain("/manager-api/data-query/admin/ontology/revisions/restore");
  });
});

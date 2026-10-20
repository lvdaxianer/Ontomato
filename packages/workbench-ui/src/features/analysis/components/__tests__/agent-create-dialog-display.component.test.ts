import { describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createI18n } from "vue-i18n";
import ElementPlus from "element-plus";
import AgentCreateDialog from "../AgentCreateDialog.vue";
import en from "../../../../locales/en";

vi.mock("../../../mcp/api", () => ({ listMcpServiceNames: vi.fn(async () => []) }));
vi.mock("../../api", () => ({
  analysisAgentApi: { createAgent: vi.fn(async () => ({})) },
  analysisTaskApi: {},
  analysisReportApi: {},
  streamDeepAnalysis: vi.fn(),
  streamFollowUp: vi.fn(),
  streamFollowUpRun: vi.fn(),
  streamTaskExecution: vi.fn(),
  cancelDeepAnalysisRun: vi.fn(),
  cancelFollowUpRun: vi.fn(),
}));
vi.mock("../../../../utils/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../../utils/api")>()),
  apiGet: vi.fn(async () => ({ data: [] })),
  apiPost: vi.fn(async () => ({ data: { classDef: [] } })),
}));
vi.mock("../../../../composables/useTheme", async () => {
  const { ref } = await import("vue");
  return { useTheme: () => ({ isDark: ref(false) }) };
});

async function dialog(extra: Record<string, unknown>) {
  const wrapper = mount(AgentCreateDialog, {
    props: { visible: false, editingAgent: null, ...extra },
    global: {
      plugins: [createI18n({ legacy: false, locale: "en", messages: { en } }), ElementPlus],
      stubs: {
        ElDialog: { template: "<div class='leave-confirmation-test'><slot/><slot name='footer'/></div>" },
        SkillSelector: true,
      },
    },
  });
  await wrapper.setProps({ visible: true });
  await flushPromises();
  return wrapper;
}

describe("display inputs of the new agent dialog under both layouts", () => {
  it("offers cancel, discard and save before leaving, and keeps the draft on a failed save", async () => {
    const { analysisAgentApi } = await import("../../api");
    const wrapper = await dialog({});
    const vm = wrapper.vm as any;
    vm.form.name = "Changed agent";
    await vm.requestClose();
    await flushPromises();
    expect(vm.leaveConfirmationVisible).toBe(true);
    const confirmation = wrapper.find(".leave-confirmation-test");
    expect(confirmation.text()).toContain("Cancel");
    expect(confirmation.text()).toContain("Discard changes");
    expect(confirmation.text()).toContain("Save");
    vi.mocked(analysisAgentApi.createAgent).mockRejectedValueOnce(new Error("Save failed"));
    await vm.saveAndClose();
    expect(wrapper.emitted("update:visible")).toBeUndefined();
    expect(vm.form.name).toBe("Changed agent");
    await vm.requestClose();
    await vm.saveAndClose();
    expect(wrapper.emitted("created")).toHaveLength(1);
    expect(wrapper.emitted("update:visible")).toEqual([[false]]);
    wrapper.unmount();
  });

  it("shows root counts only for subgraph scope", async () => {
    const wrapper = await dialog({});
    expect(wrapper.find(".scope-graph-toolbar").text()).toContain("0 classes in range");
    expect(wrapper.find(".scope-graph-toolbar").text()).not.toContain("roots");
    (wrapper.vm as any).form.graphScopeMode = "subgraph";
    await flushPromises();
    expect(wrapper.find(".scope-graph-toolbar").text()).toContain("0 roots");
    wrapper.unmount();
  });

  it("when omitted: left-column asterisk on the left, labels not wrapped", async () => {
    const wrapper = await dialog({});
    const items = wrapper.findAll(".form-left .el-form-item");
    expect(items.length).toBeGreaterThan(0);
    for (const item of items) expect(item.classes()).toContain("asterisk-left");
    expect(wrapper.find(".agent-form-layout").classes()).toContain("agent-form-layout--label-nowrap");
  });

  it("open-source input: left-column asterisk on the right, labels wrapped", async () => {
    const wrapper = await dialog({ requireAsteriskPosition: "right", labelWrap: true });
    const items = wrapper.findAll(".form-left .el-form-item");
    for (const item of items) expect(item.classes()).toContain("asterisk-right");
    expect(wrapper.find(".agent-form-layout").classes()).toContain("agent-form-layout--label-wrap");
  });
});

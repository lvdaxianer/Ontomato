import { describe, expect, it } from "vitest";
import { createApp, defineComponent, h, nextTick, ref, type App } from "vue";
import CodeEditor from "../components/CodeEditor.vue";
import { provideManagerContext, type ManagerHostContext } from "../context";
import { englishMessages } from "../i18n";

const host: ManagerHostContext = {
  apiBase: "/tenant-a/api",
  credential: { type: "apiKey", apiKey: "key-a" },
  locale: "en",
  messages: englishMessages,
  theme: "light",
  view: "/assets/metrics",
  dataFallbacks: { noBusinessDescription: "no description", unnamedAsset: "unnamed" },
  presentation: {
    themeProperties: { light: {}, dark: {} },
    nodePalette: [],
    edgeLabel: {},
    maxFitZoom: null,
    dataBrowserDescription: () => "description",
    compactDialogs: false,
  },
  knowledgeTagValues: { general: "g", business: "b", tech: "t" },
  embedded: true,
  knowledgeGovernanceUrl: null,
};

async function settle() {
  for (let index = 0; index < 6; index += 1) {
    await new Promise((resolve) => setTimeout(resolve, 0));
    await nextTick();
  }
}

function mountEditor(initial: string, formatter?: (code: string) => Promise<string>) {
  const code = ref(initial);
  const app: App = createApp(
    defineComponent({
      setup() {
        provideManagerContext(host, () => {});
        return () =>
          h(CodeEditor, {
            modelValue: code.value,
            language: "python",
            formatter,
            "onUpdate:modelValue": (value: string) => {
              code.value = value;
            },
          });
      },
    })
  );
  const element = document.createElement("div");
  document.body.appendChild(element);
  app.mount(element);
  return { app, code, element };
}

const content = (element: HTMLElement) => element.querySelector(".cm-content")?.textContent ?? "";

describe("CodeEditor", () => {
  it("renders the model value and follows external changes", async () => {
    const { app, code, element } = mountEditor("x = 1\n");
    await settle();
    expect(content(element)).toContain("x = 1");

    code.value = "y = 2\n";
    await settle();
    expect(content(element)).toContain("y = 2");
    expect(content(element)).not.toContain("x = 1");
    app.unmount();
  });

  it("a successful format replaces the document and emits the new value", async () => {
    const { app, code, element } = mountEditor("def f( a,b ):\n  return a+b\n", (source) =>
      Promise.resolve(source.replace("def f( a,b ):", "def f(a, b):"))
    );
    await settle();

    const button = [...element.querySelectorAll("button")].find(
      (item) => item.textContent?.trim() === "Format"
    ) as HTMLButtonElement;
    button.click();
    await settle();

    expect(content(element)).toContain("def f(a, b):");
    expect(code.value).toContain("def f(a, b):");
    app.unmount();
  });

  it("a rejected format leaves the document untouched", async () => {
    const { app, code, element } = mountEditor("x = 1\n", () =>
      Promise.reject(new Error("cannot parse"))
    );
    await settle();

    const button = [...element.querySelectorAll("button")].find(
      (item) => item.textContent?.trim() === "Format"
    ) as HTMLButtonElement;
    button.click();
    await settle();

    expect(content(element)).toContain("x = 1");
    expect(code.value).toBe("x = 1\n");
    app.unmount();
  });

  it("expand opens a large right-side drawer with the same code", async () => {
    const { app, element } = mountEditor("x = 1\n");
    await settle();

    const button = [...element.querySelectorAll("button")].find(
      (item) => item.textContent?.trim() === "Expand"
    ) as HTMLButtonElement;
    button.click();
    await settle();

    const drawer = document.querySelector(".el-drawer")!;
    expect(drawer.classList.contains("rtl")).toBe(true);
    expect(drawer.querySelector(".cm-content")?.textContent).toContain("x = 1");
    expect(drawer.querySelector(".code-editor")).toBeTruthy();

    (drawer.querySelector(".el-drawer__close-btn") as HTMLButtonElement).click();
    await settle();
    // The closed drawer keeps its hidden wrapper; the editor content is destroyed on close.
    expect(drawer.querySelector(".cm-content")).toBeNull();
    app.unmount();
  });
});

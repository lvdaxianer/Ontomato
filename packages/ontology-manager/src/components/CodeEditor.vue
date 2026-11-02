<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { WandSparkles } from "lucide-vue-next";
import { ElButton } from "element-plus";
import { EditorView, basicSetup } from "codemirror";
import { Compartment, EditorState } from "@codemirror/state";
import { StreamLanguage } from "@codemirror/language";
import { python } from "@codemirror/legacy-modes/mode/python";
import { oneDark } from "@codemirror/theme-one-dark";
import { useOntologyText } from "../context";

const props = withDefaults(
  defineProps<{
    modelValue: string;
    language?: string;
    // eslint-disable-next-line no-unused-vars -- base rule false-positive on TS function-type parameter names in .vue files
    formatter?: (code: string) => Promise<string>;
  }>(),
  { language: "python" }
);

const emit = defineEmits<{ "update:modelValue": [value: string] }>();
const { ot } = useOntologyText();

const editorHost = ref<HTMLDivElement | null>(null);
const formatting = ref(false);
let editor: EditorView | null = null;
let themeObserver: MutationObserver | null = null;
const themeCompartment = new Compartment();
const languageCompartment = new Compartment();

const selectionSelector =
  "&.cm-focused > .cm-scroller > .cm-selectionLayer .cm-selectionBackground, " +
  ".cm-selectionBackground, .cm-content ::selection";

const lightChrome = EditorView.theme(
  {
    "&": {
      backgroundColor: "var(--el-fill-color-lighter)",
    },
    ".cm-content": {
      caretColor: "var(--el-text-color-primary)",
    },
    ".cm-cursor": {
      borderLeftColor: "var(--el-text-color-primary)",
    },
    [selectionSelector]: {
      backgroundColor: "var(--el-color-primary-light-8)",
    },
    ".cm-gutters": {
      backgroundColor: "var(--el-fill-color-lighter)",
      color: "var(--el-text-color-placeholder)",
      border: "none",
    },
    ".cm-activeLine": {
      backgroundColor: "var(--el-fill-color-light)",
    },
    ".cm-activeLineGutter": {
      backgroundColor: "var(--el-fill-color-light)",
    },
  },
  { dark: false }
);

// Dark mode rides on the host's `html.dark` class (same signal as the workbench theme switch).
const darkChrome = EditorView.theme(
  {
    "&": {
      backgroundColor: "var(--el-bg-color)",
    },
    ".cm-gutters": {
      backgroundColor: "var(--el-bg-color)",
      color: "var(--el-text-color-placeholder)",
      border: "none",
    },
    ".cm-activeLine": {
      backgroundColor: "var(--el-fill-color-light)",
    },
    ".cm-activeLineGutter": {
      backgroundColor: "var(--el-fill-color-light)",
    },
  },
  { dark: true }
);

const isDark = () =>
  typeof document !== "undefined" && document.documentElement.classList.contains("dark");

const currentTheme = () => (isDark() ? [oneDark, darkChrome] : [lightChrome]);

function resolveLanguage(language: string) {
  switch (language) {
    case "python":
      return StreamLanguage.define(python);
    default:
      return [];
  }
}

function createEditor() {
  if (!editorHost.value || editor) return;
  editor = new EditorView({
    parent: editorHost.value,
    state: EditorState.create({
      doc: props.modelValue ?? "",
      extensions: [
        basicSetup,
        languageCompartment.of(resolveLanguage(props.language)),
        themeCompartment.of(currentTheme()),
        EditorState.tabSize.of(4),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) emit("update:modelValue", update.state.doc.toString());
        }),
      ],
    }),
  });
}

function destroyEditor() {
  themeObserver?.disconnect();
  themeObserver = null;
  editor?.destroy();
  editor = null;
}

const languageLabel = computed(() => {
  const language = (props.language || "").trim();
  return language ? language.charAt(0).toUpperCase() + language.slice(1) : "";
});

watch(
  () => props.modelValue,
  (value) => {
    const next = value ?? "";
    if (editor && next !== editor.state.doc.toString()) {
      editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: next } });
    }
  }
);

watch(
  () => props.language,
  (language) => {
    editor?.dispatch({ effects: languageCompartment.reconfigure(resolveLanguage(language)) });
  }
);

async function format() {
  if (!editor || !props.formatter || formatting.value) return;
  formatting.value = true;
  try {
    const formatted = await props.formatter(editor.state.doc.toString());
    // Only replace the document when formatting actually changed it; the update listener then syncs v-model.
    if (typeof formatted === "string" && formatted !== editor.state.doc.toString()) {
      editor.dispatch({ changes: { from: 0, to: editor.state.doc.length, insert: formatted } });
    }
  } catch {
    // The formatter owner reports the failure; the document stays untouched so no code is lost.
  } finally {
    formatting.value = false;
  }
}

onMounted(() => {
  createEditor();
  if (typeof document !== "undefined") {
    themeObserver = new MutationObserver(() => {
      editor?.dispatch({ effects: themeCompartment.reconfigure(currentTheme()) });
    });
    themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
  }
});

onUnmounted(() => {
  destroyEditor();
});

defineExpose({ format });
</script>

<template>
  <div class="code-editor">
    <div class="code-editor-toolbar">
      <span v-if="languageLabel" class="code-editor-language">{{ languageLabel }}</span>
      <span class="code-editor-spacer" />
      <el-button
        v-if="formatter"
        size="small"
        text
        type="primary"
        :icon="WandSparkles"
        :loading="formatting"
        :disabled="formatting"
        @click="format"
      >
        {{ ot("codeFormat") }}
      </el-button>
    </div>
    <div ref="editorHost" class="code-editor-host" />
  </div>
</template>

<style scoped>
.code-editor {
  width: 100%;
  border: 1px solid var(--el-border-color);
  border-radius: var(--radius-sm, 8px);
  overflow: hidden;
  background: var(--el-fill-color-lighter);
}
.code-editor-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm, 8px);
  padding: 2px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}
.code-editor-language {
  font-size: var(--text-xs, 12px);
  color: var(--el-text-color-secondary);
  letter-spacing: 0.02em;
}
.code-editor-spacer {
  flex: 1;
}
.code-editor-host {
  height: 300px;
  font-family: var(--font-mono);
  font-size: 13px;
}
.code-editor-host :deep(.cm-editor) {
  height: 100%;
  outline: none;
  color: var(--el-text-color-primary);
}
.code-editor-host :deep(.cm-scroller) {
  overflow: auto;
  font-family: var(--font-mono);
  line-height: 1.6;
}
.code-editor-host :deep(.cm-content) {
  padding: 8px 0;
}
.code-editor-host :deep(.cm-line) {
  padding: 0 8px;
}
.code-editor-host :deep(.cm-lineNumbers .cm-gutterElement) {
  padding: 0 6px 0 12px;
  min-width: 32px;
}
</style>

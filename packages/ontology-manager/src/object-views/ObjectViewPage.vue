<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from "vue";
import { ElAlert, ElButton } from "element-plus";
import { createObjectView, objectViewDocumentSchema, type ObjectReference, type ObjectViewDocument } from "@ontomato/contracts/object-views";
import { useManagerClient, useOntologyText } from "../context";
const { ot } = useOntologyText();
import ObjectViewRuntime from "./ObjectViewRuntime.vue";
import { computed } from "vue";
import { instanceName, type InstanceNameAttribute } from "./instance-name";
const props = defineProps<{ reference: ObjectReference; panel?: boolean }>();
const emit = defineEmits<{ open: [reference: ObjectReference]; edit: []; full: []; back: [] }>();
const client = useManagerClient();
const document = ref<ObjectViewDocument>();
const object = ref<Record<string, unknown>>({});
const error = ref("");
const loading = ref(false);
const nameAttributes = ref<InstanceNameAttribute[]>([]);
const displayName = computed(() => {
  const configured = nameAttributes.value.find(attr => attr.name === document.value?.titleField && attr.enable !== false && (attr.businessKey || attr.bizzkeyBool || attr.bizzkey || attr.bizzKey));
  return instanceName(configured ? [configured] : nameAttributes.value, object.value);
});
let request = 0;
onBeforeUnmount(() => { request++; });
async function load() {
  const current = ++request;
  loading.value = true;
  error.value = "";
  document.value = undefined;
  object.value = {};
  try {
    const [state, result] = await Promise.all([
      client.objectViewRequest("read", props.reference.className, props.panel ? "panel" : "full"),
      client.queryInstanceInfo(`${props.reference.className}:${props.reference.primaryKey}`, props.reference.className),
    ]);
    if (current !== request) return;
    document.value = state.versions[0] ? objectViewDocumentSchema.parse(state.versions[0].document) : createObjectView(props.reference.className);
    if (!state.versions[0]) document.value.tabs[0].sections[0].widgets[0].title = ot('ovKindProperties');
    object.value = (result.data || {}) as Record<string, unknown>;
    nameAttributes.value = (result.classDef as { attrs?: InstanceNameAttribute[] } | undefined)?.attrs || [];
  } catch (reason) { if (current === request) error.value = reason instanceof Error ? reason.message : String(reason); }
  finally { if (current === request) loading.value = false; }
}
async function refreshObject() {
  const current = ++request;
  try {
    const result = await client.queryInstanceInfo(`${props.reference.className}:${props.reference.primaryKey}`, props.reference.className);
    if (current === request) object.value = (result.data || {}) as Record<string, unknown>;
  } catch (reason) { if (current === request) error.value = reason instanceof Error ? reason.message : String(reason); }
}
watch(() => JSON.stringify([props.reference.className, props.reference.primaryKey, props.panel]), () => void load(), { immediate: true });
</script>
<template>
  <section :class="['object-view-page', { 'is-panel': panel }]">
    <header class="object-header">
      <div class="object-heading"><el-button v-if="!panel" text @click="emit('back')">{{ ot('ovBack') }}</el-button><div class="object-title"><small>{{ document?.title || reference.className }}</small><h2>{{ displayName }}</h2></div></div>
      <div class="object-actions"><el-button v-if="panel" type="primary" plain @click="emit('full')">{{ ot('ovFull') }}</el-button><el-button text @click="emit('edit')">{{ ot('ovConfigure') }}</el-button><el-button text @click="load">{{ ot('ovRefresh') }}</el-button></div>
    </header>
    <el-alert v-if="error" type="error" :title="error" :closable="false" /><p v-if="loading">{{ ot('ovLoading') }}</p>
    <ObjectViewRuntime v-else-if="document" :document="document" :object="object" :reference="reference" :panel="panel" @open="emit('open', $event)" @refresh="refreshObject" />
  </section>
</template>
<style scoped>
.object-view-page { height: 100%; min-width: 0; overflow: auto; padding: 24px; box-sizing: border-box; container: object-page / inline-size; }
.object-header { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px; padding-bottom: 20px; margin-bottom: 20px; border-bottom: 1px solid var(--el-border-color-lighter); }
.object-heading { display: flex; align-items: center; gap: 16px; min-width: 0; flex: 1 1 200px; }
.object-title { display: grid; gap: 4px; min-width: 0; }
.object-title h2 { margin: 0; font-size: 22px; font-weight: 600; line-height: 30px; overflow-wrap: anywhere; }
.object-title small { font-size: 12px; line-height: 20px; color: var(--el-text-color-secondary); overflow-wrap: anywhere; }
.object-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.object-actions :deep(.el-button + .el-button) { margin-inline-start: 0; }
.object-view-page > .el-alert { margin-bottom: 16px; }
.is-panel { padding: 0; height: auto; overflow: visible; }
.is-panel .object-header { gap: 12px; padding-bottom: 16px; margin-bottom: 16px; }
.is-panel .object-title h2 { font-size: 18px; line-height: 26px; }
.is-panel .object-actions { flex-basis: 100%; }
@container object-page (max-width: 520px) { .object-actions { flex-basis: 100%; } }
</style>

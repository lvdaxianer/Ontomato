<script setup lang="ts">
import { ref } from "vue";
import { ElMessage } from "element-plus";
import { analysisReportApi } from "../api";
import { useAnalysisStore } from "../stores/analysis";
import { useLocale } from "../../../composables/useLocale";

const store = useAnalysisStore();
const { t } = useLocale();
const visible = ref(false);
const loading = ref(false);
const instructions = ref("");
const defaults = ref("");
const title = ref("");
const subtitle = ref("");
const image = ref("");
const imageLoading = ref(false);
const imageMode = ref<"background" | "side">("side");
let imageVersion = 0;

async function open() {
  visible.value = true;
  loading.value = true;
  title.value = "";
  subtitle.value = "";
  image.value = "";
  imageMode.value = "side";
  imageVersion++;
  imageLoading.value = false;
  try {
    const response = await analysisReportApi.pptDefaults();
    defaults.value = (response?.data || response).instructions;
    instructions.value = defaults.value;
  } catch (error: any) {
    ElMessage.error(error?.message || t("analysis.pptGenerateFailed"));
    visible.value = false;
  } finally {
    loading.value = false;
  }
}

async function selectImage(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  if (
    !["image/png", "image/jpeg", "image/webp"].includes(file.type) ||
    file.size > 10 * 1024 * 1024
  ) {
    ElMessage.error(t("analysis.pptImageInvalid"));
    return;
  }
  const version = ++imageVersion;
  imageLoading.value = true;
  try {
    const bitmap = await createImageBitmap(file);
    const scale = Math.min(1, 2400 / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.round(bitmap.width * scale));
    canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    canvas
      .getContext("2d")!
      .drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    bitmap.close();
    if (version === imageVersion) image.value = canvas.toDataURL("image/png");
  } catch {
    ElMessage.error(t("analysis.pptImageInvalid"));
  } finally {
    if (version === imageVersion) imageLoading.value = false;
  }
}

async function generate() {
  if (!instructions.value.trim()) return;
  visible.value = false;
  await store.createPpt({
    userInstructions: instructions.value,
    cover: { title: title.value, subtitle: subtitle.value },
    coverImage: image.value || undefined,
    coverImageMode: imageMode.value,
  });
}

defineExpose({ open });
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="t('analysis.pptSettings')"
    width="min(720px, 94vw)"
    :close-on-click-modal="false"
    :close-on-press-escape="!store.isCreatingPpt"
    :show-close="!store.isCreatingPpt"
  >
    <div v-loading="loading">
      <el-form label-position="top" :disabled="loading || store.isCreatingPpt">
        <el-form-item :label="t('analysis.pptInstructions')">
          <el-input
            v-model="instructions"
            type="textarea"
            :rows="10"
            maxlength="10000"
            show-word-limit
          />
          <el-button link @click="instructions = defaults">{{
            t("analysis.pptReset")
          }}</el-button>
        </el-form-item>
        <el-form-item :label="t('analysis.pptCoverTitle')">
          <el-input
            v-model="title"
            maxlength="200"
            :placeholder="t('analysis.pptAutoSubtitle')"
          />
        </el-form-item>
        <el-form-item :label="t('analysis.pptCoverSubtitle')">
          <el-input
            v-model="subtitle"
            type="textarea"
            :rows="2"
            maxlength="500"
            :placeholder="t('analysis.pptAutoSubtitle')"
          />
        </el-form-item>
        <el-form-item :label="t('analysis.pptCoverImage')">
          <input
            type="file"
            accept="image/png,image/jpeg,image/webp"
            :disabled="store.isCreatingPpt || imageLoading"
            @change="selectImage"
          />
          <div v-if="image" class="cover-image">
            <img :src="image" :alt="t('analysis.pptCoverImage')" />
            <button
              type="button"
              class="remove-image"
              :aria-label="t('common.delete')"
              :title="t('common.delete')"
              @click="
                image = '';
                imageVersion++;
              "
            >
              <span aria-hidden="true">×</span>
              {{ t("common.delete") }}
            </button>
          </div>
          <el-radio-group v-if="image" v-model="imageMode" class="image-mode">
            <el-radio value="side">{{
              t("analysis.pptCoverImageSide")
            }}</el-radio>
            <el-radio value="background">{{
              t("analysis.pptCoverImageBackground")
            }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button :disabled="store.isCreatingPpt" @click="visible = false">{{
        t("common.cancel")
      }}</el-button>
      <el-button
        type="primary"
        :loading="store.isCreatingPpt"
        :disabled="
          loading || imageLoading || !instructions.trim() || !store.canCreatePpt
        "
        @click="generate"
      >
        {{ t("analysis.generatePpt") }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.cover-image {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  margin-top: 12px;
}
.cover-image img {
  max-width: 260px;
  max-height: 140px;
  object-fit: contain;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
}
.remove-image {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 6px;
  padding: 6px 10px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  cursor: pointer;
  font-size: 13px;
}
.remove-image:hover {
  border-color: var(--el-color-danger);
  background: var(--el-color-danger-light-8);
}
.remove-image span {
  transform: translateY(-1px);
}
.image-mode {
  margin-top: 12px;
}
</style>

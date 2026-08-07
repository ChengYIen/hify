<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

// =========================================================================
// Types
// =========================================================================

export type FormData = Record<string, unknown>

// =========================================================================
// Props & Model & Emits
// =========================================================================

const props = withDefaults(defineProps<{
  title: string
  width?: string
  rules?: FormRules
  /** 表单 label 宽度 */
  labelWidth?: string
  /** 表单 label 对齐方式 */
  labelPosition?: 'left' | 'right' | 'top'
}>(), {
  width: '520px',
  labelWidth: '100px',
  labelPosition: 'right',
})

const visible = defineModel<boolean>({ default: false })

const emit = defineEmits<{
  submit: [data: FormData]
}>()

// =========================================================================
// Internal State
// =========================================================================

const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const formData = reactive<FormData>({})

/** 是否处于编辑模式（open 时传入 data 则为编辑） */
const isEdit = ref(false)

// =========================================================================
// Methods
// =========================================================================

/** 清空 formData 中的所有属性 */
function clearFormData(): void {
  Object.keys(formData).forEach((key) => delete formData[key])
}

/**
 * 打开弹窗。
 * 传 data → 编辑模式（自动填充表单）；不传 → 新增模式（空表单）。
 */
function open(data?: FormData): void {
  clearFormData()
  if (data) {
    Object.assign(formData, data)
    isEdit.value = true
  } else {
    isEdit.value = false
  }
  // 清除上次的校验残留
  formRef.value?.clearValidate()
  visible.value = true
}

/** 确认提交：先校验表单，通过后 emit submit */
async function handleConfirm(): Promise<void> {
  if (!formRef.value) {
    emit('submit', { ...formData })
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    // 校验未通过 — 不提交，Element Plus 会自动高亮错误字段
    return
  }
  submitLoading.value = true
  try {
    emit('submit', { ...formData })
  } finally {
    submitLoading.value = false
  }
}

/** 关闭弹窗并重置 */
function handleClose(): void {
  visible.value = false
}

// =========================================================================
// Watchers
// =========================================================================

/** 关闭时自动清理表单数据 */
watch(visible, (val) => {
  if (!val) {
    clearFormData()
    formRef.value?.resetFields()
  }
})

// =========================================================================
// Expose
// =========================================================================

defineExpose({ open, isEdit })
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-width="labelWidth"
      :label-position="labelPosition"
      class="hify-form"
    >
      <slot :form-data="formData" />
    </el-form>

    <template #footer>
      <div class="hify-form-dialog__footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleConfirm">
          确定
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.hify-form {
  padding-right: var(--hify-space-2);
}

.hify-form-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--hify-space-3);
}
</style>

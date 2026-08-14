<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, MagicStick } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import PageHeader from '@/components/common/PageHeader.vue'
import { notifyError, notifySuccess } from '@/utils/notify'
import {
  createWorkflow,
  type WorkflowEdge,
  type WorkflowNode,
} from '@/api/workflow'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  name: '',
  description: '',
  config: getExampleWorkflowJson(),
})

const formRules: FormRules = {
  name: [{ required: true, message: '请输入工作流名称', trigger: 'blur' }],
  config: [{ required: true, message: '请输入工作流配置 JSON', trigger: 'blur' }],
}

function formatConfig(): void {
  try {
    form.config = JSON.stringify(JSON.parse(form.config), null, 2)
    notifySuccess('工作流配置已格式化')
  } catch {
    notifyError('工作流配置不是合法 JSON，无法格式化')
  }
}

async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  let parsed: { nodes?: WorkflowNode[]; edges?: WorkflowEdge[] }
  try {
    parsed = JSON.parse(form.config)
  } catch {
    notifyError('工作流配置不是合法 JSON')
    return
  }
  if (!Array.isArray(parsed.nodes) || !Array.isArray(parsed.edges)) {
    notifyError('工作流配置必须包含 nodes 和 edges 数组')
    return
  }

  submitting.value = true
  try {
    await createWorkflow({
      name: form.name,
      description: form.description || undefined,
      nodes: parsed.nodes,
      edges: parsed.edges,
    })
    notifySuccess('工作流创建成功')
    router.push('/workflows')
  } finally {
    submitting.value = false
  }
}

function getExampleWorkflowJson(): string {
  return `{
  "nodes": [
    {
      "nodeKey": "classify",
      "type": "LLM",
      "name": "问题分类",
      "config": {
        "prompt": "判断问题类型，返回：售前/售后/技术支持",
        "outputVariable": "intent"
      }
    },
    {
      "nodeKey": "router",
      "type": "CONDITION",
      "name": "路由分发",
      "config": {
        "expression": "{{intent}}",
        "outputVariable": "route"
      }
    },
    {
      "nodeKey": "presale",
      "type": "LLM",
      "name": "售前咨询",
      "config": {
        "prompt": "你是产品顾问，介绍产品功能和优势",
        "outputVariable": "answer"
      }
    },
    {
      "nodeKey": "aftersale",
      "type": "LLM",
      "name": "售后服务",
      "config": {
        "prompt": "你是售后客服，回答退换货和保修问题",
        "outputVariable": "answer"
      }
    },
    {
      "nodeKey": "techsupport",
      "type": "LLM",
      "name": "技术支持",
      "config": {
        "prompt": "你是技术工程师，帮用户排查使用问题",
        "outputVariable": "answer"
      }
    }
  ],
  "edges": [
    { "sourceNodeKey": "classify", "targetNodeKey": "router", "condition": null },
    { "sourceNodeKey": "router", "targetNodeKey": "presale", "condition": "售前" },
    { "sourceNodeKey": "router", "targetNodeKey": "aftersale", "condition": "售后" },
    { "sourceNodeKey": "router", "targetNodeKey": "techsupport", "condition": "技术支持" }
  ]
}`
}
</script>

<template>
  <div class="workflow-create-page">
    <PageHeader title="新建工作流" description="填写基本信息并粘贴工作流 JSON 配置">
      <template #actions>
        <el-button :icon="ArrowLeft" @click="router.push('/workflows')">返回列表</el-button>
      </template>
    </PageHeader>

    <el-form
      ref="formRef"
      :model="form"
      :rules="formRules"
      label-width="96px"
      class="workflow-form"
    >
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" maxlength="255" placeholder="请输入工作流名称" />
      </el-form-item>

      <el-form-item label="描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="512"
          placeholder="请输入工作流描述（可选）"
        />
      </el-form-item>

      <el-form-item label="工作流配置" prop="config">
        <div class="config-editor">
          <el-input
            v-model="form.config"
            type="textarea"
            :rows="20"
            class="config-editor__textarea"
            placeholder="请输入工作流 JSON 配置"
          />
          <div class="config-editor__actions">
            <el-button :icon="MagicStick" @click="formatConfig">格式化</el-button>
          </div>
        </div>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">提交</el-button>
        <el-button @click="router.push('/workflows')">取消</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<style scoped>
.workflow-form {
  max-width: 860px;
}

.config-editor {
  width: 100%;
}

.config-editor__textarea {
  width: 100%;
}

.config-editor__textarea :deep(.el-textarea__inner) {
  font-family: var(--hify-font-mono);
  font-size: var(--hify-font-size-sm);
  line-height: var(--hify-line-height-base);
}

.config-editor__actions {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--hify-space-2);
}
</style>

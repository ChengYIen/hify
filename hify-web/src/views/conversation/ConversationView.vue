<script setup lang="ts">
import PageHeader from '@/components/common/PageHeader.vue'
</script>

<template>
  <PageHeader
    title="对话"
    description="与 AI Agent 进行实时对话，支持流式输出和工具调用"
  >
    <template #actions>
      <el-button type="primary">新建对话</el-button>
    </template>
  </PageHeader>
</template>

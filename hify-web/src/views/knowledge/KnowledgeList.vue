<script setup lang="ts">
import PageHeader from '@/components/common/PageHeader.vue'
</script>

<template>
  <PageHeader
    title="知识库"
    description="上传和管理文档，构建 RAG 知识库供 Agent 检索"
  >
    <template #actions>
      <el-button type="primary">上传文档</el-button>
      <el-button>新建知识库</el-button>
    </template>
  </PageHeader>
</template>

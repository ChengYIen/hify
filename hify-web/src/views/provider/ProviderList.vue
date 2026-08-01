<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getHealth } from '@/api/health'

const status = ref<'loading' | 'connected' | 'disconnected'>('loading')
const message = ref('')

onMounted(async () => {
  try {
    const result = await getHealth()
    message.value = result
    status.value = 'connected'
  } catch {
    status.value = 'disconnected'
  }
})
</script>

<template>
  <div class="page-placeholder">
    <p>模型提供商管理</p>
    <p v-if="status === 'loading'" class="status-loading">正在检测后端连接...</p>
    <p v-else-if="status === 'connected'" class="status-connected">后端已连接：{{ message }}</p>
    <p v-else class="status-disconnected">后端未连接</p>
  </div>
</template>

<style scoped>
.page-placeholder {
  font-size: 24px;
  color: #303133;
  padding: 24px;
}

.status-loading {
  font-size: 16px;
  color: #909399;
  margin-top: 12px;
}

.status-connected {
  font-size: 16px;
  color: #67c23a;
  margin-top: 12px;
}

.status-disconnected {
  font-size: 16px;
  color: #f56c6c;
  margin-top: 12px;
}
</style>

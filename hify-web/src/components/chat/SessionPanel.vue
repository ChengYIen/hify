<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import type { SessionResponse } from '@/api/conversation'
import { formatRelativeTime } from '@/utils/datetime'

/**
 * 会话列表摘要最大长度：与服务端 TITLE_MAX_LENGTH=30 对齐。
 * 旧会话标题存的是完整首条消息，这里在展示层兜底截断。
 */
const SUMMARY_MAX_LENGTH = 30

/** 摘要截断：最多 30 字符，超出追加省略号 */
function summarize(title: string): string {
  return title.length > SUMMARY_MAX_LENGTH
    ? `${title.slice(0, SUMMARY_MAX_LENGTH)}…`
    : title
}

defineProps<{
  sessions: SessionResponse[]
  /** 当前打开的会话 id（null = 新对话） */
  activeId: number | null
  loading: boolean
}>()

const emit = defineEmits<{
  select: [id: number]
  create: []
}>()
</script>

<template>
  <aside class="session-panel">
    <!-- 顶部：显著的新建对话入口 -->
    <div class="session-panel__header">
      <el-button type="primary" class="new-chat-btn" :icon="Plus" @click="emit('create')">
        新建对话
      </el-button>
    </div>

    <!-- 会话列表 -->
    <div v-loading="loading" class="session-list">
      <div v-if="sessions.length === 0 && !loading" class="session-empty">
        <p class="session-empty__title">暂无历史会话</p>
        <p class="session-empty__sub">点击上方按钮开始新对话</p>
      </div>

      <div
        v-for="s in sessions"
        :key="s.id"
        class="session-item"
        :class="{ active: s.id === activeId }"
        @click="emit('select', s.id)"
      >
        <div class="session-item__title">{{ summarize(s.title || '未命名对话') }}</div>
        <div v-if="s.agentName" class="session-item__agent">{{ s.agentName }}</div>
        <div class="session-item__meta">
          <span class="session-item__time">{{ formatRelativeTime(s.updatedAt) }}</span>
          <span v-if="s.messageCount" class="session-item__count">{{ s.messageCount }} 条</span>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.session-panel {
  width: 260px;
  min-width: 260px;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--hify-bg-page);
  border-right: 1px solid var(--hify-border-light);
}

/* 顶部新建按钮 */
.session-panel__header {
  padding: var(--hify-space-4);
  border-bottom: 1px solid var(--hify-border-light);
}
.new-chat-btn {
  width: 100%;
  font-weight: 600;
}

/* 会话列表 */
.session-list {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--hify-space-2);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border-radius: var(--hify-radius-lg);
  cursor: pointer;
  transition: background-color var(--hify-transition-fast);
  user-select: none;
}
.session-item:hover {
  background: var(--hify-bg-hover);
}
.session-item.active {
  background: var(--hify-bg-active);
  box-shadow: inset 2px 0 0 var(--hify-primary-500);
}

.session-item__title {
  font-size: var(--hify-font-size-base);
  font-weight: 500;
  color: var(--hify-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-item.active .session-item__title {
  color: var(--hify-primary-600);
}

.session-item__agent {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  max-width: 100%;
  padding: 1px 8px;
  font-size: 11px;
  line-height: 18px;
  color: var(--hify-primary-600);
  background: var(--hify-primary-50);
  border-radius: var(--hify-radius-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-item__meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.session-item__time,
.session-item__count {
  font-size: var(--hify-font-size-xs);
  color: var(--hify-text-tertiary);
}

/* 空状态 */
.session-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--hify-space-1);
  padding: var(--hify-space-6);
  text-align: center;
}
.session-empty__title {
  margin: 0;
  font-size: var(--hify-font-size-base);
  font-weight: 500;
  color: var(--hify-text-secondary);
}
.session-empty__sub {
  margin: 0;
  font-size: var(--hify-font-size-sm);
  color: var(--hify-text-tertiary);
}

/* 窄屏：隐藏会话栏，保证聊天区可用 */
@media (max-width: 900px) {
  .session-panel {
    display: none;
  }
}
</style>

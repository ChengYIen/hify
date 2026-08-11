<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ChatDotRound } from '@element-plus/icons-vue'
import { useSSE } from '@/composables/useSSE'
import {
  chatStreamUrl,
  latestMessages,
  listSessions,
  type MessageResponse,
  type SessionResponse,
} from '@/api/conversation'
import SessionPanel from '@/components/chat/SessionPanel.vue'
import MessageBubble, { type ChatBubble } from '@/components/chat/MessageBubble.vue'

// =========================================================================
// 状态
// =========================================================================

let seq = 0
function nextId(): number {
  return ++seq
}

// 顶层解构 ref，模板里可自动解包（streaming / streamText / streamError）
const { text: streamText, streaming, error: streamError, connect: connectStream, abort: abortStream } = useSSE()

/** 会话列表（按 updatedAt 倒序） */
const sessions = ref<SessionResponse[]>([])
const sessionLoading = ref(false)
/** 当前会话 ID；null = 新对话（首条消息由服务端自动建会话） */
const sessionId = ref<number | null>(null)

const messages = ref<ChatBubble[]>([])
/** 当前正在流式填充的 assistant 气泡（发送按钮互斥保证同一时刻至多一个） */
const streamMessage = ref<ChatBubble | null>(null)
const input = ref('')
const listRef = ref<HTMLDivElement | null>(null)

const canSend = computed(() => input.value.trim().length > 0 && !streaming.value)
const isEmpty = computed(() => messages.value.length === 0)
const currentTitle = computed(
  () => sessions.value.find((s) => s.id === sessionId.value)?.title ?? '新对话',
)

/** 组件卸载后不再发起任何后续请求 */
let disposed = false
onBeforeUnmount(() => {
  disposed = true
  abortStream()
})

// =========================================================================
// 会话列表加载 + 切换
// =========================================================================

onMounted(() => {
  init()
})

/** 首次进入：拉会话列表，默认打开最新会话 */
async function init(): Promise<void> {
  await refreshSessions()
  const first = sessions.value[0]
  if (first?.id != null) await openSession(first.id)
}

async function refreshSessions(): Promise<void> {
  sessionLoading.value = true
  try {
    const page = await listSessions(1, 50)
    sessions.value = page.data ?? []
  } catch {
    // 拦截器已提示；保留旧列表
  } finally {
    sessionLoading.value = false
  }
}

function handleSelect(id: number): void {
  if (id === sessionId.value) return
  openSession(id)
}

async function openSession(id: number): Promise<void> {
  if (streaming.value) abortStream()
  sessionId.value = id
  messages.value = []
  streamMessage.value = null
  streamText.value = ''
  scrollToBottom()
  try {
    const list = await latestMessages(id, 50)
    messages.value = list.map(toBubble)
    scrollToBottom()
  } catch {
    // 拦截器已提示；保持空列表
  }
}

function toBubble(m: MessageResponse): ChatBubble {
  return {
    id: m.id,
    role: m.role === 'assistant' ? 'assistant' : 'user',
    content: m.content ?? '',
    streaming: false,
    error: null,
    createdAt: new Date(m.createdAt),
  }
}

// =========================================================================
// 新建对话：清空当前上下文（会话 ID 归零，下一条消息重新自动建会话）
// =========================================================================

function handleNew(): void {
  abortStream()
  streamText.value = ''
  messages.value = []
  streamMessage.value = null
  sessionId.value = null
  input.value = ''
  scrollToBottom()
}

// =========================================================================
// 发送交互（时间线）：
//   1. 点发送 → 输入框立即清空，消息区底部追加用户气泡
//   2. 紧接着出现空的 AI 气泡加载动画
//   3. fetch 手动处理 SSE 流（接口是 POST，不用 EventSource）
//   4. 每收到 delta chunk 就追加内容到 AI 气泡并滚动到底部
//   5. 收到 done / error 后移除加载动画，恢复发送按钮
// =========================================================================

async function handleSend(): Promise<void> {
  const content = input.value.trim()
  if (!content || streaming.value) return

  // 1. 输入框立即清空 + 追加用户气泡
  input.value = ''
  messages.value.push({
    id: nextId(),
    role: 'user',
    content,
    streaming: false,
    error: null,
    createdAt: new Date(),
  })

  // 2. 紧接着追加空的 AI 气泡（streaming=true → 加载动画）
  const aiMsg: ChatBubble = {
    id: nextId(),
    role: 'assistant',
    content: '',
    streaming: true,
    error: null,
    createdAt: new Date(),
  }
  messages.value.push(aiMsg)
  streamMessage.value = aiMsg
  scrollToBottom()

  // 3~5. 消费 SSE 流（URL 由是否已有会话决定）
  await connectStream(chatStreamUrl(sessionId.value), { content, stream: true })

  if (disposed) return
  if (sessionId.value == null && streamError.value == null) {
    // 首个消息走"无会话直发"自动建会话，done 后回捞最新会话供下一轮复用
    await resolveNewestSession()
  } else if (sessionId.value != null) {
    // 已有会话：消息落库后 updatedAt 前移，刷新列表排序/时间
    refreshSessions()
  }
}

/**
 * 自动建会话发生在服务端（sessionId 缺省时），done 事件不含 sessionId，
 * 这里刷新会话列表并取最新会话（列表按 updatedAt 倒序）作为当前上下文。
 */
async function resolveNewestSession(): Promise<void> {
  try {
    await refreshSessions()
    const first = sessions.value[0]
    if (first?.id != null) sessionId.value = first.id
  } catch {
    // sessionId 保持为空，下一条消息仍走自动建会话
  }
}

// 流式收尾：delta 增量已由 streamText 的 watch 实时写入气泡，这里只收尾 UI 状态
watch(streaming, (isStreaming) => {
  if (isStreaming || !streamMessage.value) return
  const bubble = streamMessage.value
  bubble.streaming = false
  bubble.error = streamError.value
  streamMessage.value = null
  scrollToBottom()
})

// 每个 delta chunk：追加内容到当前 AI 气泡 + 滚动到底部
watch(streamText, () => {
  if (streamMessage.value) {
    streamMessage.value.content = streamText.value
  }
  scrollToBottom()
})

// 停止生成：取消 fetch + 通知后端取消 LLM 调用，保留已流式的内容
function handleStop(): void {
  abortStream()
}

// =========================================================================
// 输入交互：Enter 发送，Shift + Enter 换行（中文输入法组合中不触发）
// =========================================================================

function onInputKeydown(e: KeyboardEvent): void {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    handleSend()
  }
}

function scrollToBottom(): void {
  nextTick(() => {
    const el = listRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}
</script>

<template>
  <div class="chat-layout">
    <!-- ============================================================
         左侧：会话列表（260px 固定宽）
         ============================================================ -->
    <SessionPanel
      :sessions="sessions"
      :active-id="sessionId"
      :loading="sessionLoading"
      @select="handleSelect"
      @create="handleNew"
    />

    <!-- ============================================================
         右侧：聊天区（顶部导航栏 + 消息流 + 输入区）
         ============================================================ -->
    <section class="chat-panel">
      <!-- 顶部：当前会话标题 -->
      <header class="chat-header">
        <h2 class="chat-header__title">{{ currentTitle }}</h2>
        <p class="chat-header__desc">与 AI Agent 实时对话，支持流式输出和工具调用</p>
      </header>

      <!-- 消息流：flex 自适应高度 + 滚动条，用户右 / AI 左，自动滚到底部 -->
      <div ref="listRef" class="message-list">
        <div v-if="isEmpty" class="empty-state">
          <el-icon :size="44" color="var(--hify-text-tertiary)"><ChatDotRound /></el-icon>
          <p class="empty-state__title">开始新的对话</p>
          <p class="empty-state__sub">输入你的问题，AI Agent 将流式回复</p>
        </div>

        <MessageBubble v-for="m in messages" :key="m.id" :message="m" />
      </div>

      <!-- 输入区：固定在底部，不随消息滚动 -->
      <div class="chat-input">
        <el-input
          v-model="input"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 6 }"
          resize="none"
          placeholder="输入消息，Enter 发送，Shift + Enter 换行"
          maxlength="2000"
          @keydown="onInputKeydown"
        />
        <div class="chat-input__actions">
          <!-- 流式期间：停止生成，替代发送按钮 -->
          <el-button v-if="streaming" @click="handleStop">停止生成</el-button>
          <el-button v-else type="primary" :disabled="!canSend" @click="handleSend">发送</el-button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* =================================================================
 * 整体：左右分栏，填满 content-card（App.vue 对对话路由设置了确定高度）
 * ================================================================= */
.chat-layout {
  display: flex;
  height: 100%;
  min-height: 0;
  border: 1px solid var(--hify-border-light);
  border-radius: var(--hify-radius-xl);
  background: var(--hify-bg-container);
  overflow: hidden;
}

/* =================================================================
 * 右侧聊天区
 * ================================================================= */
.chat-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chat-header {
  padding: var(--hify-space-4) var(--hify-space-6);
  border-bottom: 1px solid var(--hify-border-light);
  background: var(--hify-bg-container);
}
.chat-header__title {
  margin: 0;
  font-size: var(--hify-font-size-lg);
  font-weight: 700;
  color: var(--hify-text-primary);
  line-height: var(--hify-line-height-tight);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.chat-header__desc {
  margin: 4px 0 0;
  font-size: var(--hify-font-size-sm);
  color: var(--hify-text-tertiary);
  line-height: var(--hify-line-height-base);
}

/* =================================================================
 * 消息流
 * ================================================================= */
.message-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: var(--hify-space-6);
  display: flex;
  flex-direction: column;
  gap: var(--hify-space-4);
}

/* 空状态 */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--hify-space-2);
}
.empty-state__title {
  margin: var(--hify-space-2) 0 0;
  font-size: var(--hify-font-size-lg);
  font-weight: 600;
  color: var(--hify-text-secondary);
}
.empty-state__sub {
  margin: 0;
  font-size: var(--hify-font-size-sm);
  color: var(--hify-text-tertiary);
}

/* =================================================================
 * 输入区
 * ================================================================= */
.chat-input {
  display: flex;
  flex-direction: column;
  gap: var(--hify-space-2);
  padding: var(--hify-space-4);
  border-top: 1px solid var(--hify-border-light);
  background: var(--hify-bg-container);
}
.chat-input__actions {
  display: flex;
  justify-content: flex-end;
}
</style>

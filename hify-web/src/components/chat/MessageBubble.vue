<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { formatRelativeTime } from '@/utils/datetime'
import { renderMarkdown } from '@/utils/markdown'

/** 消息气泡模型（含流式 UI 状态） */
export interface ChatBubble {
  id: number
  role: 'user' | 'assistant'
  content: string
  /** 仅 assistant：true = 展示流式加载动画（流式进行中且尚无内容） */
  streaming: boolean
  /** 仅 assistant：流式出错时展示错误原因（保留已流式的部分内容） */
  error: string | null
  /** 消息时间（用于相对时间标签） */
  createdAt: Date
}

const props = defineProps<{ message: ChatBubble }>()

// =========================================================================
// 打字机效果：流式期间 content 由父组件持续追加（完整累计文本），
// 这里只控制"已展示"的部分，按 30ms/字符 逐字显示到气泡。
//  - 流式中且出现新字符 → 启动/继续逐字展示
//  - 流式结束（done/error）或历史消息（streaming=false）→ 立即展示全部
// =========================================================================

/** 打字机每字符间隔（ms） */
const TYPEWRITER_INTERVAL = 30

const display = ref('')
let shown = 0 // 已展示字符数（content 的前 shown 个字符）
let timer: ReturnType<typeof setTimeout> | null = null
let ticking = false // 定时器链是否在跑

function stopTimer(): void {
  if (timer) {
    clearTimeout(timer)
    timer = null
  }
  ticking = false
}

function tick(): void {
  ticking = true
  timer = setTimeout(() => {
    // 每次触发时重新读取最新 content，运行中的定时器链会自动拾取流式新字符
    const content = props.message.content
    if (shown < content.length) {
      display.value += content.charAt(shown)
      shown += 1
      tick()
    } else {
      ticking = false
      timer = null
    }
  }, TYPEWRITER_INTERVAL)
}

/** 立即展示剩余全部字符（流式结束 / 历史消息 / 组件卸载兜底） */
function flushAll(content: string): void {
  stopTimer()
  if (shown < content.length) {
    display.value += content.slice(shown)
    shown = content.length
  }
}

watch(
  () => [props.message.content, props.message.streaming] as const,
  ([content, streaming]) => {
    if (!streaming) {
      flushAll(content)
      return
    }
    // 流式中：有新字符且定时器链空闲则启动；运行中会自动拾取新到达的字符
    if (!ticking && shown < content.length) tick()
  },
  { immediate: true },
)

onBeforeUnmount(stopTimer)

// =========================================================================
// Markdown 渲染：assistant 气泡在"非流式"（历史消息 / 流式结束 done·error）时
// 把完整 content 渲染成 HTML；流式期间保持打字机纯文本，避免残缺 Markdown
// 逐字重排导致闪烁。用户气泡保持纯文本（isRendered 恒为 false）。
// =========================================================================

const isRendered = computed(
  () => props.message.role === 'assistant' && !props.message.streaming,
)

const renderedHtml = computed(() =>
  isRendered.value ? renderMarkdown(props.message.content) : '',
)
</script>

<template>
  <div class="bubble-row" :class="message.role === 'user' ? 'is-user' : 'is-assistant'">
    <div class="bubble-avatar">{{ message.role === 'user' ? '我' : 'AI' }}</div>
    <div class="bubble-wrap">
      <div class="bubble" :class="message.role === 'user' ? 'bubble--user' : 'bubble--assistant'">
        <!-- 空 AI 气泡加载动画：流式进行中且尚无内容 -->
        <span v-if="message.streaming && !message.content" class="stream-loading">
          <span class="stream-loading__dots"><i /><i /><i /></span>
        </span>
        <!-- 内容（含流式实时增量）；出错时额外展示错误原因 -->
        <template v-else>
          <!-- assistant 非流式渲染 Markdown；流式中 / 用户气泡走打字机纯文本 -->
          <div v-if="message.content" class="bubble-text" :class="{ 'is-rendered': isRendered }">
            <div v-if="isRendered" class="markdown-body" v-html="renderedHtml"></div>
            <span v-else>{{ display }}</span>
          </div>
          <div v-if="message.error" class="stream-error">{{ message.error }}</div>
        </template>
      </div>
      <div class="bubble-time">{{ formatRelativeTime(message.createdAt) }}</div>
    </div>
  </div>
</template>

<style scoped>
.bubble-row {
  display: flex;
  align-items: flex-end;
  gap: var(--hify-space-2);
}
.bubble-row.is-user {
  flex-direction: row-reverse;
}

.bubble-avatar {
  width: 30px;
  height: 30px;
  min-width: 30px;
  border-radius: var(--hify-radius-full);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
}
.bubble-row.is-user .bubble-avatar {
  background: linear-gradient(135deg, var(--hify-primary-500), var(--hify-primary-600));
}
.bubble-row.is-assistant .bubble-avatar {
  background: linear-gradient(135deg, var(--hify-accent-500), var(--hify-accent-600));
}

.bubble-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-width: 76%;
}
.bubble-row.is-user .bubble-wrap {
  align-items: flex-end;
}
.bubble-row.is-assistant .bubble-wrap {
  align-items: flex-start;
}

.bubble {
  max-width: 100%;
  padding: 10px 14px;
  border-radius: var(--hify-radius-xl);
  font-size: var(--hify-font-size-base);
  line-height: var(--hify-line-height-loose);
  word-break: break-word;
}
.bubble--user {
  background: linear-gradient(135deg, var(--hify-primary-500), var(--hify-primary-600));
  color: #fff;
  border-top-right-radius: var(--hify-radius-sm);
}
.bubble--assistant {
  background: var(--hify-bg-container);
  border: 1px solid var(--hify-border-light);
  border-top-left-radius: var(--hify-radius-sm);
  box-shadow: var(--hify-shadow-xs);
}

.bubble-text {
  white-space: pre-wrap;
}

/* Markdown 渲染态：换行交给 <p>/<br>，不再依赖 pre-wrap */
.bubble-text.is-rendered {
  white-space: normal;
}

/* =================================================================
 * Markdown 渲染内容（v-html 注入，需 :deep 穿透）
 * ================================================================= */
/* 代码块 / 行内代码：等宽字体 */
.markdown-body :deep(pre),
.markdown-body :deep(code) {
  font-family: var(--hify-font-mono, Consolas, 'Courier New', monospace);
}
.markdown-body :deep(pre) {
  margin: var(--hify-space-2) 0;
  padding: var(--hify-space-3);
  background: var(--hify-bg-page);
  border: 1px solid var(--hify-border-light);
  border-radius: var(--hify-radius-sm);
  overflow-x: auto;
  font-size: var(--hify-font-size-sm);
  line-height: var(--hify-line-height-base);
}
.markdown-body :deep(code) {
  font-size: 0.9em;
  background: var(--hify-bg-hover);
  padding: 0.15em 0.4em;
  border-radius: 4px;
}
.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
  font-size: inherit;
}

/* 基础排版：标题 / 列表 / 链接 / 引用等，让 Markdown 在气泡内可读 */
.markdown-body :deep(p) {
  margin: var(--hify-space-1) 0;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
  margin: var(--hify-space-3) 0 var(--hify-space-1);
  font-weight: 600;
  line-height: var(--hify-line-height-tight);
}
.markdown-body :deep(h1) {
  font-size: 1.25em;
}
.markdown-body :deep(h2) {
  font-size: 1.15em;
}
.markdown-body :deep(h3) {
  font-size: 1.05em;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  margin: var(--hify-space-1) 0;
  padding-left: 1.5em;
}
.markdown-body :deep(li) {
  margin: 2px 0;
}
.markdown-body :deep(a) {
  color: var(--hify-text-brand);
  text-decoration: underline;
}
.markdown-body :deep(blockquote) {
  margin: var(--hify-space-2) 0;
  padding: 2px 12px;
  border-left: 3px solid var(--hify-border-light);
  color: var(--hify-text-secondary);
}
.markdown-body :deep(hr) {
  margin: var(--hify-space-3) 0;
  border: none;
  border-top: 1px solid var(--hify-border-light);
}
.markdown-body :deep(table) {
  margin: var(--hify-space-2) 0;
  border-collapse: collapse;
  font-size: var(--hify-font-size-sm);
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  padding: 4px 8px;
  border: 1px solid var(--hify-border-light);
}
.markdown-body :deep(th) {
  background: var(--hify-bg-hover);
  font-weight: 600;
}

/* 时间标签：紧贴气泡下方、与气泡同侧对齐 */
.bubble-time {
  font-size: var(--hify-font-size-xs);
  color: var(--hify-text-tertiary);
  line-height: 1.4;
  padding: 0 2px;
}

/* 流式加载动画：三个弹跳点 */
.stream-loading {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
}
.stream-loading__dots {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.stream-loading__dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--hify-text-tertiary);
  animation: hify-bounce 1.2s infinite ease-in-out;
}
.stream-loading__dots i:nth-child(2) {
  animation-delay: 0.15s;
}
.stream-loading__dots i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes hify-bounce {
  0%,
  80%,
  100% {
    transform: translateY(0);
    opacity: 0.4;
  }
  40% {
    transform: translateY(-4px);
    opacity: 1;
  }
}

.stream-error {
  margin-top: 4px;
  color: var(--hify-error);
  font-size: var(--hify-font-size-sm);
}
</style>

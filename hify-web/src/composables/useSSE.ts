import { ref, type Ref } from 'vue'

interface SSEMessage {
  type: 'delta' | 'done' | 'error'
  content?: string
  finishReason?: string
  latencyMs?: number
  messageId?: number
  model?: string
  message?: string
}

/** 认证失败统一跳转登录（与 utils/request.ts 拦截器行为一致） */
function redirectToLogin(): void {
  localStorage.removeItem('token')
  window.location.href = '/login'
}

/** 从非 SSE 响应体里尽量提取错误原因（后端统一返回 Result.fail 的 JSON） */
function extractErrorMessage(payload: string): string | null {
  if (!payload) return null
  try {
    const body: unknown = JSON.parse(payload)
    const message = (body as { message?: unknown })?.message
    if (typeof message === 'string' && message) return message
    return null
  } catch {
    // 非 JSON（如网关错误页）：截断展示原文
    return payload.length > 200 ? `${payload.slice(0, 200)}…` : payload
  }
}

/**
 * 基于 fetch 的手动 SSE 客户端 —— 流式接口是 POST，EventSource 无法携带请求体，
 * 必须用 fetch + ReadableStream 手动解析。
 *
 * <p>解析规则：只认 {@code data:} 开头的行（Spring SseEmitter 写 {@code data:{json}}，
 * 冒号后无空格，因此不能用 {@code "data: "} 匹配）；后端心跳 comment（{@code :ping}）
 * 以 {@code :} 开头，天然跳过。类型化事件：
 * {@code {"type":"delta","content":...}} 逐段追加、{@code {"type":"done",...}} 结束、
 * {@code {"type":"error","message":...}} 出错。</p>
 *
 * <p>非 SSE 响应兜底：SseEmitter 未返回时抛出的 {@code BizException} 会走全局异常处理器
 * 返回 JSON {@code Result.fail}（HTTP 200），此时 body 不是 event-stream，解析 JSON 里的
 * {@code message} 写入 {@code error}，保证 UI 能展示会话创建失败、无可用模型等业务原因。</p>
 */
export function useSSE() {
  const text: Ref<string> = ref('')
  const streaming: Ref<boolean> = ref(false)
  const error: Ref<string | null> = ref(null)
  const finishReason: Ref<string | null> = ref(null)
  const latencyMs: Ref<number | null> = ref(null)
  const messageId: Ref<number | null> = ref(null)
  let abortController: AbortController | null = null

  async function connect(url: string, body: Record<string, unknown> = {}) {
    text.value = ''
    streaming.value = true
    error.value = null
    finishReason.value = null
    latencyMs.value = null
    messageId.value = null
    abortController = new AbortController()

    try {
      const token = localStorage.getItem('token')
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(body),
        signal: abortController.signal,
      })

      if (response.status === 401) {
        redirectToLogin()
        return
      }

      // 非 SSE 响应：HTTP 错误 或 后端返回的业务 JSON 错误（如自动建会话失败）
      const contentType = response.headers.get('content-type') ?? ''
      if (!response.ok || !contentType.includes('text/event-stream')) {
        const payload = await response.text().catch(() => '')
        error.value = extractErrorMessage(payload) || `请求失败（HTTP ${response.status}）`
        return
      }

      const reader = response.body?.getReader()
      if (!reader) throw new Error('No response body')

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (!line.startsWith('data:')) continue
          const data = line.slice(5).trim()
          if (!data || data === '[DONE]') {
            streaming.value = false
            return
          }
          try {
            const msg: SSEMessage = JSON.parse(data)
            switch (msg.type) {
              case 'delta':
                text.value += msg.content ?? ''
                break
              case 'done':
                finishReason.value = msg.finishReason ?? null
                latencyMs.value = msg.latencyMs ?? null
                messageId.value = msg.messageId ?? null
                streaming.value = false
                return
              case 'error':
                error.value = msg.message ?? '对话出错'
                streaming.value = false
                return
              default:
                // 未知类型事件：忽略
                break
            }
          } catch {
            // 非 JSON 数据行：按纯文本追加
            text.value += data
          }
        }
      }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') return
      const message = err instanceof Error ? err.message : 'SSE 连接异常'
      error.value = message
    } finally {
      streaming.value = false
    }
  }

  function abort() {
    abortController?.abort()
    streaming.value = false
  }

  return { text, streaming, error, finishReason, latencyMs, messageId, connect, abort }
}

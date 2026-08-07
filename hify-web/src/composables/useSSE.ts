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
      const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: abortController.signal,
      })

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`)
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
          if (!line.startsWith('data: ')) continue
          const data = line.slice(6)
          if (data === '[DONE]') {
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

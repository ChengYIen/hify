import { ref, type Ref } from 'vue'

interface SSEMessage {
  content: string
  done: boolean
  error?: string
}

export function useSSE() {
  const text: Ref<string> = ref('')
  const streaming: Ref<boolean> = ref(false)
  const error: Ref<string | null> = ref(null)
  let abortController: AbortController | null = null

  async function connect(url: string, body: Record<string, unknown> = {}) {
    text.value = ''
    streaming.value = true
    error.value = null
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
          if (line.startsWith('data: ')) {
            const data = line.slice(6)
            if (data === '[DONE]') {
              streaming.value = false
              return
            }
            try {
              const msg: SSEMessage = JSON.parse(data)
              text.value += msg.content
              if (msg.done) {
                streaming.value = false
                return
              }
            } catch {
              text.value += data
            }
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

  return { text, streaming, error, connect, abort }
}

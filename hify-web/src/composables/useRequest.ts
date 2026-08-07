import { ref, type Ref } from 'vue'

/**
 * 请求状态管理 composable。
 * 自动管理 data / loading / error 三态，避免每个页面写 try-catch-finally 样板代码。
 *
 * @example
 * const { data, loading, error, execute } = useRequest(() => api.listAgents())
 * await execute()
 */
export function useRequest<T>(apiFn: () => Promise<T>) {
  const data: Ref<T | null> = ref(null)
  const loading: Ref<boolean> = ref(false)
  const error: Ref<string | null> = ref(null)

  async function execute(): Promise<T | null> {
    loading.value = true
    error.value = null
    try {
      const result = await apiFn()
      data.value = result as T
      return result
    } catch (e: unknown) {
      const message = e instanceof Error ? e.message : '请求失败'
      error.value = message
      return null
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, execute }
}

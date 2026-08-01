import { ref, type Ref } from 'vue'

interface PageResult<T> {
  list: T[]
  cursor: string | null
  hasMore: boolean
}

export function usePagination<T>(fetchFn: (cursor: string | null) => Promise<PageResult<T>>) {
  const list: Ref<T[]> = ref([])
  const cursor: Ref<string | null> = ref(null)
  const hasMore: Ref<boolean> = ref(true)
  const loading: Ref<boolean> = ref(false)

  async function loadMore() {
    if (loading.value || !hasMore.value) return
    loading.value = true
    try {
      const result = await fetchFn(cursor.value)
      list.value.push(...result.list)
      cursor.value = result.cursor
      hasMore.value = result.hasMore
    } finally {
      loading.value = false
    }
  }

  function reset() {
    list.value = []
    cursor.value = null
    hasMore.value = true
  }

  return { list, hasMore, loading, loadMore, reset }
}

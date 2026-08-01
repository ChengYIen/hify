import { useUserStore } from '@/stores/user'
import { ref, type Ref } from 'vue'

export function useAuth() {
  const userStore = useUserStore()
  const loading: Ref<boolean> = ref(false)
  const error: Ref<string | null> = ref(null)

  async function login(username: string, password: string) {
    loading.value = true
    error.value = null
    try {
      const response = await fetch('/api/v1/public/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      })
      const result = await response.json()
      if (result.code !== 0) {
        throw new Error(result.message || '登录失败')
      }
      const { id, displayName, token } = result.data
      localStorage.setItem('token', token)
      userStore.setLogin({ id, username, displayName, token })
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : '登录异常'
      error.value = message
    } finally {
      loading.value = false
    }
  }

  function logout() {
    localStorage.removeItem('token')
    userStore.logout()
  }

  return { loading, error, login, logout, getUser: () => userStore }
}

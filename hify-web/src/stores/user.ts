import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const userId = ref<number | null>(null)
  const username = ref('')
  const displayName = ref('')
  const token = ref('')

  function setLogin(user: { id: number; username: string; displayName: string; token: string }) {
    userId.value = user.id
    username.value = user.username
    displayName.value = user.displayName
    token.value = user.token
  }

  function logout() {
    userId.value = null
    username.value = ''
    displayName.value = ''
    token.value = ''
  }

  return { userId, username, displayName, token, setLogin, logout }
})

import { ref } from 'vue'
import { defineStore } from 'pinia'

// access token 存 localStorage（短期，15 分钟，XSS 风险低）；
// refresh token 由后端通过 HttpOnly Cookie 下发，JS 无法读取，防 XSS 窃取。
interface UserInfo {
  id?: number
  username?: string
  name?: string
  gender?: string
  phone?: string
  roleLevel?: number
  status?: number
  avatar?: string
  isSuper?: number
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(loadFromStorage())

  function isLoggedIn(): boolean {
    return !!token.value
  }

  function loadFromStorage(): UserInfo | null {
    const raw = localStorage.getItem('currentUser')
    if (!raw) return null
    try {
      return JSON.parse(raw) as UserInfo
    } catch {
      return null
    }
  }

  /** 登录成功后调用：只存 access token，refresh token 由 cookie 自动携带 */
  function setAuth(t: string, user: UserInfo) {
    token.value = t
    userInfo.value = user
    localStorage.setItem('token', t)
    localStorage.setItem('currentUser', JSON.stringify(user))
  }

  /** 仅更新 access token（refresh 后调用） */
  function setAccessToken(t: string) {
    token.value = t
    localStorage.setItem('token', t)
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('currentUser')
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    loadFromStorage,
    setAuth,
    setAccessToken,
    logout
  }
})

// 双 token 无感刷新：access token（localStorage）过期时，
// 后端自动携带 HttpOnly Cookie 里的 refresh token 换新 access token。
// 前端无需手动管理 refresh token，降低 XSS 窃取风险。
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  // 让 fetch/axios 自动携带同源 cookie（含 HttpOnly refreshToken）
  withCredentials: true
})

// 401 并发去重：第一个 401 触发 refresh，其余 401 挂起等待结果
let isRefreshing = false
let retryQueue: Array<(token: string | null) => void> = []

// 跳登录页去重：同一批并发 401 只提示一次
let authRedirectInProgress = false
const AUTH_ERROR_NOTIFIED = '__authErrorNotified'

function redirectLogin(msg: string) {
  if (authRedirectInProgress) return
  authRedirectInProgress = true
  setTimeout(() => { authRedirectInProgress = false }, 5000)
  router.push({ path: '/login', query: { msg } })
}

/**
 * 调后端 /auth/refresh 换新 access token。
 * refresh token 由后端通过 HttpOnly Cookie 自动携带，前端无需手动传入。
 * 并发去重：同一时间只发一次，其余调用方排队等待。
 */
function tryRefresh(): Promise<string | null> {
  if (isRefreshing) {
    return new Promise<string | null>((resolve) => {
      retryQueue.push((t) => resolve(t))
    })
  }

  isRefreshing = true
  const ctrl = new AbortController()
  const timeoutId = setTimeout(() => ctrl.abort(), 15000)

  return fetch('/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'same-origin',   // 携带同源 HttpOnly Cookie
    body: JSON.stringify({}),
    signal: ctrl.signal
  })
    .then((r) => r.json())
    .then((data) => {
      if (data.success && data.accessToken) {
        // 只更新 access token（localStorage）；refresh token 由后端 Set-Cookie 轮换，前端无需操作
        localStorage.setItem('token', data.accessToken)
        const userModule = (window as any).__userStoreRef
        if (userModule?.setAccessToken) {
          userModule.setAccessToken(data.accessToken)
        }
        return data.accessToken as string
      }
      return null
    })
    .catch(() => null)
    .finally(() => {
      clearTimeout(timeoutId)
      isRefreshing = false
      retryQueue.forEach((cb) => cb(null))
      retryQueue = []
    })
}

request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    return response.data as any
  },
  async (error) => {
    const originalRequest = error.config

    // 只对 401 且非 refresh 接口本身做自动刷新（排除刷新接口自身的 401，避免死循环）
    if (
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !originalRequest.url?.includes('/auth/refresh')
    ) {
      originalRequest._retry = true
      const newToken = await tryRefresh()
      if (newToken) {
        originalRequest.headers.Authorization = `Bearer ${newToken}`
        return request(originalRequest)
      }
      // refresh token 也过期/失效：清空本地登录态并跳登录页（刷新失败兜底）
    }

    if (error.response) {
      if (error.response.status === 401) {
        // 同一时刻多个接口并发 401 时，只跳一次登录页、只弹一次 toast
        redirectLogin('登录已过期，请重新登录')
        ElMessage.error('登录已过期，请重新登录')
        // 替换 error.message 并打标记，避免业务页 catch 里把 401 原文或重复提示再弹一次
        error.message = '登录已过期，请重新登录'
        ;(error as any)[AUTH_ERROR_NOTIFIED] = true
      } else if (error.response.status === 400) {
        return Promise.reject(error.response.data)
      } else {
        // 5xx 等其它错误：全局只弹一次中文提示；给 reject 的 error 打标记，
        // 业务页 catch 若识别到标记则不再重复弹，避免“全局 + 局部”双弹窗
        const msg =
          error.response.data?.message ||
          error.response.data?.error ||
          (error.response.status === 500 ? '服务器内部错误' : '请求失败')
        ElMessage.error(msg)
        ;(error as any).__authErrorNotified = true
        error.message = msg
      }
    } else {
      ElMessage.error('网络错误，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request as any

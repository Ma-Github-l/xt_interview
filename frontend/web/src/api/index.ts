import request from '@/utils/request'

// 认证相关 API
export const authApi = {
  login(data: { username: string; password: string }) {
    return request.post('/auth/login', data)
  },
  logout() {
    return request.post('/auth/logout')
  },
  validate() {
    return request.get('/auth/validate')
  }
}

// 账号管理 API
export const accountApi = {
  getAll() {
    return request.get('/accounts')
  },
  getById(id: number) {
    return request.get(`/accounts/${id}`)
  },
  create(data: any) {
    return request.post('/accounts', data)
  },
  update(id: number, data: any) {
    return request.put(`/accounts/${id}`, data)
  },
  delete(id: number) {
    return request.delete(`/accounts/${id}`)
  },
  getPage(params: {
    page?: number
    size?: number
    username?: string
    status?: number | null
    roleLevel?: number | null
    deleted?: number | null
  }) {
    const query: Record<string, any> = {
      page: params.page ?? 0,
      size: params.size ?? 10
    }
    if (params.username) query.username = params.username
    if (params.status !== undefined && params.status !== null) query.status = params.status
    if (params.roleLevel !== undefined && params.roleLevel !== null) query.roleLevel = params.roleLevel
    if (params.deleted !== undefined && params.deleted !== null) query.deleted = params.deleted
    return request.get('/accounts/page', { params: query })
  },

  /** 恢复已删除用户（仅超级管理员） */
  restore(id: number) {
    return request.post(`/accounts/${id}/restore`)
  }
}

// 部门管理 API
export const departmentApi = {
  getAll() {
    return request.get('/departments')
  },
  getById(id: number) {
    return request.get(`/departments/${id}`)
  },
  create(data: any) {
    return request.post('/departments', data)
  },
  update(id: number, data: any) {
    return request.put(`/departments/${id}`, data)
  },
  delete(id: number) {
    return request.delete(`/departments/${id}`)
  },
  getPage(params: {
    page?: number
    size?: number
    name?: string
    code?: string
    status?: number | null
    deleted?: number | null
  }) {
    const query: Record<string, any> = {
      page: params.page ?? 0,
      size: params.size ?? 10
    }
    if (params.name) query.name = params.name
    if (params.code) query.code = params.code
    if (params.status !== undefined && params.status !== null) query.status = params.status
    if (params.deleted !== undefined && params.deleted !== null) query.deleted = params.deleted
    return request.get('/departments/page', { params: query })
  },

  /** 恢复已删除部门（仅超级管理员） */
  restore(id: number) {
    return request.post(`/departments/${id}/restore`)
  }
}

// 头像 API
export const avatarApi = {
  /** 上传当前登录用户的头像，返回 { avatar: "avatar_1.png" } */
  upload(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    const token = localStorage.getItem('token')
    return request.post('/avatars', formData, {
      headers: { 'Content-Type': 'multipart/form-data', Authorization: `Bearer ${token}` },
      timeout: 60000
    })
  },
  /**
   * 管理员上传其他用户的头像。
   * GET /api/avatars/{id} 是"上传指定用户头像"端点（targetUserId 由后端鉴权控制），
   * 避免普通用户通过 /api/avatars 端点覆盖其他用户头像。
   */
  uploadForUser(targetUserId: number | null, file: File) {
    if (targetUserId == null) {
      return Promise.reject(new Error('缺少目标用户ID'))
    }
    const formData = new FormData()
    formData.append('file', file)
    const token = localStorage.getItem('token')
    return request.post(`/avatars/${targetUserId}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data', Authorization: `Bearer ${token}` },
      timeout: 60000
    })
  },
  /** 拼接头像完整 URL（供 el-avatar 或 img src 使用） */
  url(fileName: string | null | undefined): string {
    if (!fileName) {
      return '/api/avatars/avatar_default.png'
    }
    return `/api/avatars/${fileName}`
  }
}

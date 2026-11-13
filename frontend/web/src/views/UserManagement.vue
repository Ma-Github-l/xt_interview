<template>
  <div class="user-management">
    <div class="page-header">
      <el-button v-if="canCreateUser" type="primary" :icon="Plus" @click="openCreateDialog">
        添加用户
      </el-button>
      <el-button :icon="Refresh" @click="loadUsers">刷新</el-button>
    </div>

    <el-form :inline="true" class="search-bar" @submit.prevent="handleSearch">
      <el-form-item label="用户名">
        <el-input
          v-model="searchForm.username"
          placeholder="请输入用户名"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="权限级别">
        <el-select v-model="searchForm.roleLevel" placeholder="全部" clearable style="width: 160px">
          <el-option label="用户权限" :value="1" />
          <el-option label="管理员权限" :value="2" />
          <el-option label="超级管理员权限" :value="3" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="isSuper" label="是否删除">
        <el-select v-model="searchForm.deleted" placeholder="未删除" style="width: 140px">
          <el-option label="未删除" :value="0" />
          <el-option label="已删除" :value="1" />
        </el-select>
      </el-form-item>
      <div class="search-actions">
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button :icon="Back" @click="handleResetSearch">重置</el-button>
      </div>
    </el-form>

    <el-table
      v-loading="loading"
      :data="users"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column label="头像" width="80" align="center">
        <template #default="{ row }">
          <el-avatar :size="40" :src="avatarUrl(row.avatar)" class="user-avatar-cell">
            <span>{{ avatarFallback(row) }}</span>
          </el-avatar>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="gender" label="性别" width="80" />
      <el-table-column prop="phone" label="联系方式" width="140" />
      <el-table-column prop="departmentName" label="所属部门" width="120">
        <template #default="{ row }">
          {{ row.departmentName || '未分配' }}
        </template>
      </el-table-column>
      <el-table-column label="权限级别" width="120">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.roleLevel)">
            {{ roleText(row.roleLevel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="180" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <!-- 已删除视图：仅显示"还原"（查到了待还原数据才给每条加），隐藏编辑/删除 -->
          <template v-if="row.deleted === 1">
            <el-button type="success" link :icon="RefreshIcon" @click="handleRestore(row)">
              还原
            </el-button>
          </template>
          <!-- 正常视图：按角色显示编辑/删除 -->
          <template v-else-if="row.deleted !== 1">
            <el-button v-if="canEditUser(row)" type="primary" link :icon="Edit" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button v-if="canDeleteUser(row)" type="danger" link :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
            <el-button v-if="!canEditUser(row) && !canDeleteUser(row)" type="info" link>
              无权限
            </el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="handlePageChange"
        @current-change="handlePageChange"
      />
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '添加用户'"
      width="500px"
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="90px"
      >
        <el-form-item label="头像">
          <div class="avatar-uploader">
            <el-avatar :size="72" :src="avatarUrl(formData.avatar)" class="avatar-preview">
              <span>{{ avatarFallback(formData) }}</span>
            </el-avatar>
            <el-upload
              :show-file-list="false"
              :before-upload="handleAvatarUpload"
              accept="image/png,image/jpeg,image/jpg,image/gif,image/webp"
            >
              <el-button size="small" :loading="avatarUploading">
                <el-icon><Upload /></el-icon> 上传头像
              </el-button>
            </el-upload>
            <div v-if="formData.avatar" class="avatar-hint">
              当前：{{ formData.avatar }}
            </div>
          </div>
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formData.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="姓名" prop="name">
          <el-input v-model="formData.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="formData.gender" placeholder="请选择性别" style="width: 100%">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
          </el-select>
        </el-form-item>
        <el-form-item label="联系方式" prop="phone">
          <el-input v-model="formData.phone" placeholder="请输入联系方式" />
        </el-form-item>
        <el-form-item label="所属部门" prop="departmentId">
          <el-select
            v-model="formData.departmentId"
            placeholder="请选择部门（可选）"
            clearable
            style="width: 100%"
          >
            <el-option
              v-for="dept in departments"
              :key="dept.id"
              :label="dept.name"
              :value="dept.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            show-password
            :placeholder="isEdit ? '留空则不修改密码' : '留空则默认123456'"
          />
        </el-form-item>
        <el-form-item label="权限级别" prop="roleLevel">
        <el-select v-model="formData.roleLevel" placeholder="请选择权限级别" style="width: 100%">
            <el-option label="用户权限" :value="1" />
            <el-option label="管理员权限" :value="2" />
          <el-option v-if="canAssignSuper" label="超级管理员权限" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formData.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { accountApi, departmentApi, avatarApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Refresh, Search, Back, Upload, Refresh as RefreshIcon } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

import { useUserStore } from '@/stores/user'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number>(0)
const avatarUploading = ref(false)

const userStore = useUserStore()

const users = ref<any[]>([])
const departments = ref<any[]>([])

// ===== 权限控制 =====
// 超级管理员（isSuper=1 或 roleLevel>=3）
const isSuper = computed(() => {
  const u = userStore.userInfo
  return !!u && (u.isSuper === 1 || (u.roleLevel ?? 0) >= 3)
})
// 管理员及以上（roleLevel>=2）
const isAdminOrAbove = computed(() => {
  const u = userStore.userInfo
  return !!u && (u.roleLevel ?? 0) >= 2
})
// 当前登录人 ID
const myId = computed(() => userStore.userInfo?.id ?? null)

// 创建用户：管理员及以上（普通用户不能新增）
const canCreateUser = computed(() => isAdminOrAbove.value)
// 表单中能否选择"超级管理员权限"级别
const canAssignSuper = computed(() => isSuper.value)

function canEditUser(row: any): boolean {
  if (isSuper.value) return true
  if (isAdminOrAbove.value) {
    // 管理员不能编辑其他超级管理员
    return !(row.isSuper === 1 && (row.roleLevel ?? 0) >= 3)
  }
  // 普通用户仅能编辑自己
  return row.id === myId.value
}

function canDeleteUser(row: any): boolean {
  if (isSuper.value) return true
  if (isAdminOrAbove.value) {
    return !(row.isSuper === 1 && (row.roleLevel ?? 0) >= 3)
  }
  // 普通用户不能删除任何人（包括自己）
  return false
}

const searchForm = reactive({
  username: '',
  status: null as number | null,
  roleLevel: null as number | null,
  // 是否删除：0=未删除，1=已删除，null=未删除（默认）
  deleted: 0 as number | null
})

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const formRef = ref<FormInstance>()

const formData = reactive({
  username: '',
  name: '',
  gender: '男',
  phone: '',
  password: '',
  roleLevel: 1,
  status: 1,
  isSuper: 0,
  departmentId: null as number | null,
  avatar: '' as string
})

const formRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  roleLevel: [{ required: true, message: '请选择权限级别', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

function roleText(level: number): string {
  const map: Record<number, string> = { 1: '用户权限', 2: '管理员权限', 3: '超级管理员' }
  return map[level] || '未知'
}

function roleTagType(level: number): 'info' | 'warning' | 'danger' {
  const map: Record<number, 'info' | 'warning' | 'danger'> = {
    1: 'info',
    2: 'warning',
    3: 'danger'
  }
  return map[level] || 'info'
}

/** 头像 URL，带时间戳参数避免浏览器缓存旧图 */
function avatarUrl(name: string | null | undefined): string {
  const url = avatarApi.url(name || undefined)
  // 每次重新渲染时 bust cache，确保上传新头像后立即显示
  return url + (url.includes('?') ? '&' : '?') + '_t=' + Date.now()
}

function avatarFallback(row: any): string {
  const name = row?.name || row?.username || '?'
  return name.charAt(0).toUpperCase()
}

async function loadDepartments() {
  try {
    departments.value = await departmentApi.getAll()
  } catch {
    departments.value = []
  }
}

async function loadUsers() {
  loading.value = true
  try {
    const result = await accountApi.getPage({
      page: pagination.page - 1,
      size: pagination.size,
      username: searchForm.username || undefined,
      status: searchForm.status,
      roleLevel: searchForm.roleLevel,
      deleted: isSuper.value ? searchForm.deleted : 0
    })
    users.value = result.list
    pagination.total = result.totalElements
  } catch (error: any) {
    if (!(error as any)?.__authErrorNotified) ElMessage.error(error.message || '加载用户列表失败')
  } finally {
    loading.value = false
  }
}

async function handleRestore(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定要还原用户 "${row.username}" 吗？还原后默认禁用，需手动启用。`,
      '还原确认',
      { confirmButtonText: '确定还原', cancelButtonText: '取消', type: 'warning' }
    )
    await accountApi.restore(row.id)
    ElMessage.success('用户已还原')
    await loadUsers()
  } catch (error: any) {
    if (error !== 'cancel') {
      if (!(error as any)?.__authErrorNotified) ElMessage.error(error.message || '还原失败')
    }
  }
}

function handleSearch() {
  pagination.page = 1
  loadUsers()
}

function handleResetSearch() {
  searchForm.username = ''
  searchForm.status = null
  searchForm.roleLevel = null
  searchForm.deleted = 0
  pagination.page = 1
  loadUsers()
}

function handlePageChange() {
  loadUsers()
}

function handleAvatarUpload(file: File): boolean {
  const currentUserId = userStore.userInfo?.id ?? null
  // 自己上传 → /api/avatars；管理员为他人上传 → /api/avatars/{targetId}（按角色鉴权）
  const isSelf = currentUserId !== null && editId.value === currentUserId
  const targetId = isSelf ? null : editId.value
  // 创建用户时（editId=0）还没有账号可挂头像：把文件暂存到待上传队列，
  // 创建成功后由 handleSubmit 自动补传到新账号
  const isCreating = !isEdit.value
  if (isCreating) {
    pendingAvatar.value = file
    formData.avatar = `pending:${Date.now()}`
    ElMessage.info('头像已暂存，创建用户后自动上传')
    return false
  }

  avatarUploading.value = true
  const uploadPromise = isSelf
    ? avatarApi.upload(file)
    : avatarApi.uploadForUser(targetId, file)
  uploadPromise
    .then((res: any) => {
      const fileName = res?.avatar
      if (!fileName) {
        ElMessage.error('头像上传失败')
        return
      }
      formData.avatar = fileName
      if (isSelf) {
        ElMessage.success('头像已更新')
        // 同步右上角头像
        userStore.userInfo = { ...userStore.userInfo, avatar: fileName }
        localStorage.setItem('currentUser', JSON.stringify(userStore.userInfo))
        loadUsers()
      } else {
        ElMessage.success('头像已保存')
        // 立即刷新列表，让表格中该用户的头像同步更新
        loadUsers()
      }
    })
    .catch((err: any) => {
      if (!(err as any)?.__authErrorNotified) ElMessage.error(err?.message || '头像上传失败')
    })
    .finally(() => {
      avatarUploading.value = false
    })
  return false
}

/** 创建用户时暂存的头像文件；创建成功后补传到新账号 */
const pendingAvatar = ref<File | null>(null)

function resetForm() {
  formData.username = ''
  formData.name = ''
  formData.gender = '男'
  formData.phone = ''
  formData.password = ''
  formData.roleLevel = 1
  formData.status = 1
  formData.isSuper = 0
  formData.departmentId = null
  formData.avatar = ''
  isEdit.value = false
  editId.value = 0
  pendingAvatar.value = null
  formRef.value?.resetFields()
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row: any) {
  resetForm()
  isEdit.value = true
  editId.value = row.id
  formData.username = row.username
  formData.name = row.name
  formData.gender = row.gender || '男'
  formData.phone = row.phone || ''
  formData.roleLevel = row.roleLevel
  formData.status = row.status
  formData.isSuper = row.isSuper
  formData.departmentId = row.departmentId ?? null
  formData.avatar = row.avatar || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    const isCreating = !isEdit.value
    try {
      const data = { ...formData }
      if (data.password === '') {
        (data as any).password = undefined
      }
      if (data.departmentId === null || data.departmentId === undefined) {
        data.departmentId = null
      }
      // 创建用户时暂存的头像先不传给后端（还没账号可挂），创建成功后再补传
      if (isCreating && pendingAvatar.value) {
        ;(data as any).avatar = undefined
      }

      if (isEdit.value) {
        await accountApi.update(editId.value, data)
        ElMessage.success('用户更新成功')
      } else {
        const created = await accountApi.create(data)
        // 创建成功后，把暂存的头像补传到新账号
        if (pendingAvatar.value && created?.id) {
          const res = await avatarApi.uploadForUser(created.id, pendingAvatar.value)
          if (res?.avatar) {
            ElMessage.success('头像已补传到新用户')
          }
        }
        pendingAvatar.value = null
        formData.avatar = ''
        ElMessage.success('用户创建成功')
      }
      dialogVisible.value = false
      await loadUsers()
    } catch (error: any) {
      if (!(error as any)?.__authErrorNotified) ElMessage.error(error.message || '操作失败')
    } finally {
      submitting.value = false
    }
  })
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 "${row.username}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await accountApi.delete(row.id)
    ElMessage.success('用户删除成功')
    await loadUsers()
  } catch (error: any) {
    if (error !== 'cancel') {
      if (!(error as any)?.__authErrorNotified) ElMessage.error(error.message || '删除失败')
    }
  }
}

onMounted(() => {
  loadDepartments()
  loadUsers()
})
</script>

<style scoped>
.user-management {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}

.page-header {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.search-bar {
  display: flex;
  flex-wrap: wrap;
  row-gap: 8px;
  align-items: center;
  margin-bottom: 16px;
}

.search-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
  align-items: center;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.user-avatar-cell {
  display: block;
  border-radius: 50%;
  border: 1px solid #e4e7ed;
}

.avatar-uploader {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.avatar-preview {
  border-radius: 50%;
  border: 2px solid #e4e7ed;
}

.avatar-hint {
  font-size: 12px;
  color: #909399;
}
</style>

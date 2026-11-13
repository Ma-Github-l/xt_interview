<template>
  <div class="department-management">
    <div class="page-header">
      <el-button v-if="canManageDepartment" type="primary" :icon="Plus" @click="openCreateDialog">
        添加部门
      </el-button>
      <el-button :icon="Refresh" @click="loadDepartments">刷新</el-button>
    </div>

    <el-form :inline="true" class="search-bar" @submit.prevent="handleSearch">
      <el-form-item label="部门名称">
        <el-input
          v-model="searchForm.name"
          placeholder="请输入部门名称"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="部门编码">
        <el-input
          v-model="searchForm.code"
          placeholder="请输入部门编码"
          clearable
          style="width: 160px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 120px">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="canManageDepartment" label="是否删除">
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
      :data="departments"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column prop="code" label="部门编码" width="140" />
      <el-table-column prop="name" label="部门名称" width="160" />
      <el-table-column prop="description" label="部门描述" min-width="200" />
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
          <!-- 已删除行：仅显示"还原"，隐藏编辑/删除/只读 -->
          <template v-if="row.deleted === 1">
            <el-button type="success" link :icon="RefreshIcon" @click="handleRestore(row)">
              还原
            </el-button>
          </template>
          <!-- 正常视图：按角色显示编辑/删除/只读 -->
          <template v-else-if="row.deleted !== 1">
            <el-button v-if="canManageDepartment" type="primary" link :icon="Edit" @click="openEditDialog(row)">
              编辑
            </el-button>
            <el-button v-if="canManageDepartment" type="danger" link :icon="Delete" @click="handleDelete(row)">
              删除
            </el-button>
            <el-button v-if="!canManageDepartment" type="info" link>
              只读
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
      :title="isEdit ? '编辑部门' : '添加部门'"
      width="500px"
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="90px"
      >
        <el-form-item label="部门编码" prop="code">
          <el-input v-model="formData.code" placeholder="请输入部门编码" />
        </el-form-item>
        <el-form-item label="部门名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入部门名称" />
        </el-form-item>
        <el-form-item label="部门描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="3"
            placeholder="请输入部门描述"
          />
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
import { departmentApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Refresh, Search, Back, Refresh as RefreshIcon } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// 部门写操作（增删改）仅超级管理员可操作；其余角色只读
const canManageDepartment = computed(() => {
  const u = userStore.userInfo
  if (!u) return false
  return u.isSuper === 1 || (u.roleLevel ?? 0) >= 3
})

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref<number>(0)

const departments = ref<any[]>([])

const searchForm = reactive({
  name: '',
  code: '',
  status: null as number | null,
  // 是否删除：0=未删除，1=已删除，null=未删除（默认）
  deleted: 0 as number | null
})

// el-pagination is 1-based, backend /departments/page is 0-based.
// Convert page to 0-based when calling the API.
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const formRef = ref<FormInstance>()

const formData = reactive({
  code: '',
  name: '',
  description: '',
  status: 1
})

const formRules: FormRules = {
  code: [{ required: true, message: '请输入部门编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

async function loadDepartments() {
  loading.value = true
  try {
    const result = await departmentApi.getPage({
      page: pagination.page - 1,
      size: pagination.size,
      name: searchForm.name || undefined,
      code: searchForm.code || undefined,
      status: searchForm.status,
      deleted: canManageDepartment.value ? searchForm.deleted : 0
    })
    departments.value = result.list
    pagination.total = result.totalElements
  } catch (error: any) {
    if (!(error as any)?.__authErrorNotified) ElMessage.error(error.message || '加载部门列表失败')
  } finally {
    loading.value = false
  }
}

async function handleRestore(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定要还原部门 "${row.name}" 吗？还原后默认禁用，需手动启用。`,
      '还原确认',
      { confirmButtonText: '确定还原', cancelButtonText: '取消', type: 'warning' }
    )
    await departmentApi.restore(row.id)
    ElMessage.success('部门已还原')
    await loadDepartments()
  } catch (error: any) {
    if (error !== 'cancel') {
      if (!(error as any)?.__authErrorNotified) ElMessage.error(error.message || '还原失败')
    }
  }
}

function handleSearch() {
  pagination.page = 1
  loadDepartments()
}

function handleResetSearch() {
  searchForm.name = ''
  searchForm.code = ''
  searchForm.status = null
  searchForm.deleted = 0
  pagination.page = 1
  loadDepartments()
}

function handlePageChange() {
  loadDepartments()
}

function resetForm() {
  formData.code = ''
  formData.name = ''
  formData.description = ''
  formData.status = 1
  isEdit.value = false
  editId.value = 0
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
  formData.code = row.code
  formData.name = row.name
  formData.description = row.description || ''
  formData.status = row.status
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      const data = { ...formData }
      if (data.description === '') {
        (data as any).description = undefined
      }

      if (isEdit.value) {
        await departmentApi.update(editId.value, data)
        ElMessage.success('部门更新成功')
      } else {
        await departmentApi.create(data)
        ElMessage.success('部门创建成功')
      }
      dialogVisible.value = false
      await loadDepartments()
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
      `确定要删除部门 "${row.name}" 吗？`,
      '删除确认',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    await departmentApi.delete(row.id)
    ElMessage.success('部门删除成功')
    await loadDepartments()
  } catch (error: any) {
    if (error !== 'cancel') {
      if (!(error as any)?.__authErrorNotified) ElMessage.error(error.message || '删除失败')
    }
  }
}

onMounted(() => {
  loadDepartments()
})
</script>

<style scoped>
.department-management {
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
</style>

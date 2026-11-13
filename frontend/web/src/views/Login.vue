<template>
  <div class="login-container">
    <div class="login-card">
      <h2 class="login-title">欣铁 面试项目</h2>
      <p class="login-subtitle">用户管理系统登录</p>

      <el-alert
        class="login-tip"
        title="默认账号：admin / 123456"
        type="info"
        :closable="false"
        show-icon
      />

      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="formData.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="formData.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            style="width: 100%"
            :loading="loading"
            native-type="submit"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const formRef = ref<FormInstance>()

const formData = reactive({
  username: '',
  password: ''
})

const formRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 把 store 暴露给 request.ts 的无感刷新逻辑，刷新成功后同步 access token
;(window as any).__userStoreRef = userStore

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    loading.value = true
    try {
      const res = await authApi.login({
        username: formData.username,
        password: formData.password
      })
      if (res.success) {
// refresh token 由后端 HttpOnly Cookie 下发，前端只存 access token
userStore.setAuth(res.accessToken || res.token, res.user)
        ElMessage.success('登录成功')
        const redirect = (route.query.redirect as string) || '/users'
        router.push(redirect)
      } else {
        ElMessage.error(res.message || '登录失败')
      }
    } catch (error: any) {
      if (!(error as any)?.__authErrorNotified) ElMessage.error(error.message || '登录失败')
    } finally {
      loading.value = false
    }
  })
}

onMounted(() => {
  userStore.loadFromStorage()
})

onBeforeUnmount(() => {
  delete (window as any).__userStoreRef
})
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
}

.login-card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 40px 32px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

.login-title {
  font-size: 24px;
  font-weight: 700;
  color: #1e3c72;
  text-align: center;
  margin: 0 0 8px;
}

.login-subtitle {
  font-size: 14px;
  color: #909399;
  text-align: center;
  margin: 0 0 16px;
}

.login-tip {
  margin-bottom: 20px;
}

.login-alert {
  margin-bottom: 16px;
}

.login-alert :deep(.el-alert__title) {
  font-size: 14px;
}

.login-tip :deep(.el-alert__title) {
  font-size: 14px;
}
</style>

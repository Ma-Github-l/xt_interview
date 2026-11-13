<template>
  <el-container class="layout-container">
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <h2>欣铁 面试</h2>
      </div>
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <!-- 部门管理仅超级管理员可见 -->
        <el-menu-item v-if="canSeeDepartment" index="/departments">
          <el-icon><OfficeBuilding /></el-icon>
          <span>部门管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <el-tooltip :content="avatarTooltip" placement="bottom">
            <el-avatar
              :size="36"
              :src="avatarSrc"
              @click="handleAvatarClick"
              class="user-avatar"
            >
              <span class="avatar-fallback">{{ avatarFallbackText }}</span>
            </el-avatar>
          </el-tooltip>
          <el-button type="danger" plain size="small" @click="handleLogout">
            退出登录
          </el-button>
        </div>
      </el-header>

      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { authApi, avatarApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { User, OfficeBuilding } from '@element-plus/icons-vue'

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()

const activeMenu = ref('/users')

// 部门管理菜单可见性：仅超级管理员（roleLevel=3 且 isSuper=1）
const canSeeDepartment = computed(() => {
  const u = userStore.userInfo
  if (!u) return false
  const isSuper = u.isSuper === 1 || (u.roleLevel ?? 0) >= 3
  return isSuper
})

// 头像文件名变化时刷新 bustCacheKey，computed 重新求值后 src 带新时间戳，浏览器拉取最新图
const bustCacheKey = ref(0)
watch(
  () => userStore.userInfo?.avatar,
  () => { bustCacheKey.value++ }
)

const avatarSrc = computed(() => {
  const avatar = userStore.userInfo?.avatar
  const url = avatarApi.url(avatar)
  return url + '?_t=' + bustCacheKey.value
})

const avatarTooltip = computed(() => {
  const name = userStore.userInfo?.name || userStore.userInfo?.username || '未知用户'
  return `${name}，点击进入用户管理`
})

const avatarFallbackText = computed(() => {
  const name = userStore.userInfo?.name || userStore.userInfo?.username || '?'
  return name.charAt(0).toUpperCase()
})

const pageTitle = computed(() => {
  const titleMap: Record<string, string> = {
    '/users': '用户管理',
    '/departments': '部门管理'
  }
  return titleMap[route.path] || '首页'
})

function handleMenuSelect(index: string) {
  router.push(index)
}

function handleAvatarClick() {
  router.push('/users')
}

onMounted(() => {
  userStore.loadFromStorage()
  if (!userStore.isLoggedIn) {
    // 未登录直接进登录页，不弹任何提示，避免与 401 拦截器的弹窗重复
    router.push('/login')
  }
  activeMenu.value = route.path
})

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await authApi.logout()
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch {
    // user cancelled
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background: #1e3c72;
  background: linear-gradient(180deg, #1e3c72 0%, #2a5298 100%);
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.logo h2 {
  color: #fff;
  font-size: 20px;
  font-weight: 600;
}

.sidebar-menu {
  background: transparent;
  border-right: none;
}

.sidebar-menu .el-menu-item {
  color: rgba(255, 255, 255, 0.85);
  font-size: 15px;
}

.sidebar-menu .el-menu-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.sidebar-menu .el-menu-item.is-active {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.header {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-avatar {
  cursor: pointer;
  border: 2px solid #e4e7ed;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.user-avatar:hover {
  transform: scale(1.08);
  border-color: #409eff;
}

.avatar-fallback {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
  background: #409eff;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.main-content {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>

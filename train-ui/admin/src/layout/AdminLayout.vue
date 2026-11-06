<template>
  <el-container class="app-shell">
    <el-aside class="app-aside" :class="{ 'is-collapsed': collapsed }">
      <div class="app-logo">
        <img class="logo-mark" :src="logoMark" alt="" />
        <span class="logo-text">铁旅通票务系统<small>管理端</small></span>
      </div>
      <div class="aside-nav">
        <el-menu
          :default-active="$route.path"
          :collapse="collapsed"
          :collapse-transition="false"
          router
        >
          <el-menu-item index="/welcome" :data-tip="collapsed ? '首页' : undefined">
            <el-icon><HomeFilled /></el-icon>
            <template #title>首页</template>
          </el-menu-item>
          <template v-for="dir in menuGroups" :key="dir.id">
            <div v-if="!collapsed" class="nav-group">{{ dir.menuName }}</div>
            <div v-else class="nav-sep"></div>
            <el-menu-item
              v-for="child in dir.children"
              :key="child.id"
              :index="resolvePath(dir.path, child.path)"
              :data-tip="collapsed ? child.menuName : undefined"
            >
              <el-icon><component :is="menuIcon(child.icon)" /></el-icon>
              <template #title>{{ child.menuName }}</template>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
    </el-aside>

    <el-container class="app-main">
      <el-header class="app-header" height="56px">
        <button
          type="button"
          class="header-icon-btn"
          :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
          :title="collapsed ? '展开侧栏' : '收起侧栏'"
          @click="toggleCollapse"
        >
          <el-icon><Expand v-if="collapsed" /><Fold v-else /></el-icon>
        </button>
        <div class="header-title">{{ $route.meta.title || '' }}</div>
        <span class="header-spacer"></span>
        <ThemeToggle />
        <el-dropdown trigger="click" @command="onCommand">
          <span class="header-user">
            <span class="header-avatar"><img :src="adminAvatar" alt="" /></span>
            <span class="header-name">{{ auth.username || auth.nickname || '管理员' }}</span>
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="app-content"><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { useAuthStore } from '../stores/auth'
import { menuIcon } from '../utils/menuIcons'
import ThemeToggle from '../components/ThemeToggle.vue'
import logoMark from '../assets/logo/logo-bold-128.png'
import adminAvatar from '../assets/avatar/avatar-admin-transparent-256.png'

const COLLAPSE_KEY = 'ntrain-admin-collapsed'

const auth = useAuthStore()
const router = useRouter()
const menuTree = ref([])
const collapsed = ref(readCollapsed())

const menuGroups = computed(() =>
  menuTree.value
    .map((dir) => ({
      ...dir,
      children: (dir.children || []).filter((child) =>
        child.menuType === 'C' && child.path && Number(child.visible ?? 1) !== 0)
    }))
    .filter((dir) => dir.children.length)
)

function readCollapsed() {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1'
  } catch (e) {
    return false
  }
}

function toggleCollapse() {
  collapsed.value = !collapsed.value
  try {
    localStorage.setItem(COLLAPSE_KEY, collapsed.value ? '1' : '0')
  } catch (e) {
    /* 隐私模式下忽略 */
  }
}

function resolvePath(parent, child) {
  if (!child) return ''
  return child.startsWith('/') ? child : `${parent || ''}/${child}`
}

onMounted(async () => {
  if (!auth.username) {
    try {
      await auth.me()
    } catch (e) {
      /* 401 由拦截器处理 */
    }
  }
  try {
    menuTree.value = await request.get('/auth/menus')
  } catch (e) {
    /* 401 由拦截器处理 */
  }
})

function onCommand(cmd) {
  if (cmd === 'logout') {
    auth.logout().finally(() => router.push('/login'))
  }
}
</script>

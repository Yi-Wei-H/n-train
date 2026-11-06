<template>
  <div class="page">
    <div class="welcome-head">
      <div>
        <h1>欢迎回来，{{ auth.username || auth.nickname || '管理员' }}</h1>
      </div>
      <el-button type="primary" plain @click="$router.push('/business/train')">
        <el-icon><Van /></el-icon>去车次管理
      </el-button>
    </div>

    <div class="welcome-stats">
      <div class="welcome-stat">
        <div class="k">接入服务</div>
        <div class="v">6</div>
        <div class="d">网关 + 5 个服务</div>
      </div>
      <div class="welcome-stat">
        <div class="k">业务域</div>
        <div class="v">3</div>
        <div class="d">系统 / 业务 / AI</div>
      </div>
      <div class="welcome-stat">
        <div class="k">菜单权限</div>
        <div class="v">{{ permCount }}</div>
        <div class="d">按角色授权</div>
      </div>
      <div class="welcome-stat">
        <div class="k">当前角色</div>
        <div class="v">{{ auth.superAdmin ? '超管' : '普管' }}</div>
        <div class="d">{{ auth.superAdmin ? '可见全部菜单' : '按角色授权' }}</div>
      </div>
    </div>

    <div class="welcome-cols">
      <el-card shadow="never">
        <h3 class="welcome-card-title">常用入口</h3>
        <div class="quick-list">
          <router-link class="quick-item" to="/business/train">
            <span class="quick-icon"><el-icon><Van /></el-icon></span>
            <span><span class="t">车次管理</span><span class="s">车次、车厢模板与经停</span></span>
          </router-link>
          <router-link class="quick-item" to="/business/order">
            <span class="quick-icon"><el-icon><Tickets /></el-icon></span>
            <span><span class="t">订单管理</span><span class="s">订单明细、发车与完成</span></span>
          </router-link>
          <router-link class="quick-item" to="/agent/model">
            <span class="quick-icon"><el-icon><Cpu /></el-icon></span>
            <span><span class="t">模型配置</span><span class="s">上游格式、认证与连通性</span></span>
          </router-link>
          <router-link class="quick-item" to="/agent/conversation">
            <span class="quick-icon"><el-icon><ChatDotRound /></el-icon></span>
            <span><span class="t">会话管理</span><span class="s">会话、消息与标题状态</span></span>
          </router-link>
          <router-link class="quick-item" to="/system/user">
            <span class="quick-icon"><el-icon><User /></el-icon></span>
            <span><span class="t">管理员</span><span class="s">账号、部门与角色</span></span>
          </router-link>
          <router-link class="quick-item" to="/system/job">
            <span class="quick-icon"><el-icon><Timer /></el-icon></span>
            <span><span class="t">定时任务</span><span class="s">每日车次与兜底扫描</span></span>
          </router-link>
        </div>
      </el-card>

      <el-card shadow="never">
        <h3 class="welcome-card-title">服务端口</h3>
        <div class="welcome-list">
          <div v-for="svc in services" :key="svc.name" class="welcome-list-row">
            <span>{{ svc.name }}</span>
            <span class="spacer"></span>
            <span class="mono">{{ svc.port }}</span>
            <el-tag type="info">已配置</el-tag>
          </div>
        </div>
      </el-card>
    </div>

    <el-card shadow="never">
      <h3 class="welcome-card-title">系统说明</h3>
      <el-alert
        type="info"
        :closable="false"
        title="菜单与权限"
        description="左侧菜单由后端 sys_menu 动态下发；主题已有浅色 / 深色 / 跟随系统。超级管理员可见全部菜单，普通管理员按角色授权；登录后菜单与按钮权限由后端下发并缓存在本地。"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()

const permCount = computed(() => {
  if (auth.superAdmin) return '全部'
  try {
    const perms = JSON.parse(localStorage.getItem('ntrain-admin-perms') || '[]')
    return perms.includes('*') ? '全部' : perms.length
  } catch (e) {
    return 0
  }
})

const services = [
  { name: '网关', port: 8000 },
  { name: 'member', port: 8001 },
  { name: 'admin', port: 8002 },
  { name: 'agent', port: 8004 },
  { name: 'business', port: 8005 },
  { name: 'task', port: 8006 }
]
</script>

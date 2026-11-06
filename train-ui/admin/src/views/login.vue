<template>
  <div class="login-page">
    <div class="login-theme"><ThemeToggle /></div>
    <el-card class="login-card" shadow="never">
      <div class="login-brand">
        <img class="logo-mark" :src="logoBlue" alt="" />
        <span class="logo-text">铁旅通票务系统</span>
      </div>
      <h1 class="login-title">管理台</h1>
      <p class="login-sub">后台管理入口</p>
      <el-form class="login-form" :model="form" @keyup.enter="submit">
        <el-form-item>
          <el-input v-model="form.username" size="large" placeholder="用户名" autocomplete="username">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            size="large"
            show-password
            placeholder="密码"
            autocomplete="current-password"
          >
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-button type="primary" size="large" class="login-submit" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>
      <div class="login-meta">铁旅通票务系统 · 管理端</div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import ThemeToggle from '../components/ThemeToggle.vue'
import logoBlue from '../assets/logo/logo-blue-256.png'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    router.push('/welcome')
  } catch (e) {
    /* 请求拦截器已提示错误 */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page tabbed me-page">
    <header class="page-head">
      <h1 class="t-page">我的</h1>
    </header>

    <section class="profile-card card">
      <span class="profile-avatar"><img :src="avatarUser" alt="" /></span>
      <div class="grow">
        <div class="profile-name">{{ user.member?.nickname || '乘客' }}</div>
        <div class="caption">{{ maskPhone(user.member?.phone) }}</div>
      </div>
      <button type="button" class="btn sm quiet edit-btn" @click="openEdit">
        <AppIcon name="edit" size="sm" />编辑
      </button>
    </section>

    <section class="card menu-card">
      <button type="button" class="menu-row" @click="$router.push('/orders')">
        <span class="menu-icon"><AppIcon name="list" size="lg" /></span>
        <span class="grow">我的订单</span>
        <AppIcon name="chevron-right" class="muted" />
      </button>
      <button type="button" class="menu-row" @click="$router.push('/my-tickets')">
        <span class="menu-icon"><AppIcon name="ticket" size="lg" /></span>
        <span class="grow">我的车票</span>
        <AppIcon name="chevron-right" class="muted" />
      </button>
      <button type="button" class="menu-row" @click="$router.push('/wallet')">
        <span class="menu-icon"><AppIcon name="wallet" size="lg" /></span>
        <span class="grow">我的钱包</span>
        <AppIcon name="chevron-right" class="muted" />
      </button>
      <button type="button" class="menu-row" @click="$router.push('/passengers')">
        <span class="menu-icon"><AppIcon name="users" size="lg" /></span>
        <span class="grow">乘车人管理</span>
        <AppIcon name="chevron-right" class="muted" />
      </button>
    </section>

    <section class="card appearance-card">
      <div class="card-head">
        <h2>外观</h2>
        <span class="caption">浅色 / 深色 / 跟随系统</span>
      </div>
      <div class="appearance-body">
        <ThemeToggle block />
      </div>
    </section>

    <div class="logout-wrap">
      <button type="button" class="btn danger-plain touch block" @click="logout">退出登录</button>
    </div>

    <!-- 个人信息编辑：姓名可改（同步本人乘车人）；手机号=登录账号、身份证=实名信息，均不可改 -->
    <van-popup v-model:show="editShow" position="bottom" round>
      <div class="sheet">
        <div class="sheet-grab" />
        <div class="sheet-title">个人信息</div>
        <div class="sheet-form">
          <label class="field">
            <span class="field-label">姓名</span>
            <input v-model="editForm.name" class="input" placeholder="与证件一致的真实姓名" maxlength="20" />
          </label>
          <label class="field">
            <span class="field-label">手机号</span>
            <input :value="maskPhone(user.member?.phone)" class="input" readonly />
            <span class="field-hint">登录账号，不可修改</span>
          </label>
          <label class="field">
            <span class="field-label">身份证</span>
            <input :value="maskIdCard(user.member?.idCardNo)" class="input" readonly />
            <span class="field-hint">实名信息，不可修改</span>
          </label>
        </div>
        <div class="sheet-actions">
          <button type="button" class="btn neutral touch" @click="editShow = false">取消</button>
          <button type="button" class="btn solid touch" @click="saveProfile">保存</button>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { showConfirmDialog } from 'vant'
import { showSuccessToast, showToast } from '../utils/toast'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { useUserStore } from '../stores/user'
import { maskPhone, maskIdCard } from '../utils/mask'
import AppIcon from '../components/AppIcon.vue'
import ThemeToggle from '../components/ThemeToggle.vue'
import avatarUser from '../assets/avatar/avatar-user-transparent-256.png'

const router = useRouter()
const user = useUserStore()

const editShow = ref(false)
const editForm = reactive({ name: '' })

function openEdit() {
  editForm.name = user.member?.nickname || ''
  editShow.value = true
}

async function saveProfile() {
  if (!editForm.name || editForm.name.trim().length < 2) {
    return showToast('请填写真实姓名')
  }
  await request.put('/user/profile', null, { params: { nickname: editForm.name.trim() } })
  await user.me()
  showSuccessToast('已保存，本人乘车人已同步')
  editShow.value = false
}

async function logout() {
  try {
    await showConfirmDialog({ title: '退出登录' })
  } catch { return }
  user.logout()
  showSuccessToast('已退出')
  router.push('/login')
}
</script>

<style scoped>
.me-page {
  padding-bottom: calc(var(--tabbar-h) + 24px + env(safe-area-inset-bottom));
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0 16px;
  padding: 16px;
}

.profile-avatar {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: var(--action-soft);
}

.profile-avatar img {
  width: 44px;
  height: 44px;
  object-fit: contain;
}

.profile-name {
  font-size: 18px;
  line-height: 26px;
  font-weight: 600;
  color: var(--text-1);
}

.edit-btn {
  flex-shrink: 0;
  color: var(--action-text);
}

.menu-card {
  margin: 12px 16px 0;
  overflow: hidden;
}

.menu-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 52px;
  padding: 8px 16px;
  border: 0;
  background: transparent;
  color: var(--text-1);
  font: 400 15px/24px var(--font-sans);
  text-align: left;
  cursor: pointer;
}

.menu-row + .menu-row {
  border-top: 1px solid var(--border);
}

.menu-row:active {
  background: var(--bg-subtle);
}

.menu-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--bg-subtle);
  color: var(--text-2);
}

.appearance-card {
  margin: 12px 16px 0;
}

.appearance-body {
  padding: 0 16px 16px;
}

.appearance-body :deep(.theme-toggle.block button) {
  height: 40px;
}

.logout-wrap {
  margin: 24px 16px 0;
}

.sheet-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>

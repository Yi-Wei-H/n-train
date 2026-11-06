<template>
  <router-view v-slot="{ Component }">
    <transition :name="pageTransition || 'none'">
      <component :is="Component" />
    </transition>
  </router-view>
  <!-- 底部导航：仅在主 Tab 页显示 -->
  <nav v-if="route.meta.tab" class="tabbar" aria-label="主导航">
    <router-link
      v-for="item in tabs"
      :key="item.path"
      :to="item.path"
      :class="{ on: route.path === item.path }"
    >
      <AppIcon :name="item.icon" size="lg" />
      <span>{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<script setup>
import { useRoute } from 'vue-router'
import AppIcon from './components/AppIcon.vue'
import { pageTransition } from './utils/pageTransition'

const route = useRoute()
const tabs = [
  { path: '/', label: '首页', icon: 'home' },
  { path: '/ai', label: 'AI', icon: 'chat' },
  { path: '/my-tickets', label: '车票', icon: 'ticket' },
  { path: '/me', label: '我的', icon: 'user' }
]
</script>

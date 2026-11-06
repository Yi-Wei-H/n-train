<template>
  <header class="page-nav">
    <button v-if="back" class="nav-back" type="button" aria-label="返回" @click="goBack">
      <AppIcon name="chevron-left" size="lg" />
    </button>
    <h1>{{ title }}</h1>
    <div class="nav-right">
      <slot name="right" />
    </div>
  </header>
</template>

<script setup>
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'

defineProps({
  title: { type: String, default: '' },
  back: { type: Boolean, default: true }
})

const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/')
  }
}
</script>

<template>
  <div class="theme-toggle" :class="{ block }" role="group" aria-label="外观主题">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      :class="{ on: themeMode === opt.value }"
      :aria-label="opt.label"
      :aria-pressed="themeMode === opt.value"
      @click="applyTheme(opt.value)"
    >
      <AppIcon :name="opt.icon" size="sm" />
    </button>
  </div>
</template>

<script setup>
import AppIcon from './AppIcon.vue'
import { applyTheme, themeMode } from '../utils/theme'

defineProps({
  block: { type: Boolean, default: false }
})

const options = [
  { value: 'light', label: '浅色', icon: 'sun' },
  { value: 'dark', label: '深色', icon: 'moon' },
  { value: 'auto', label: '跟随系统', icon: 'monitor' }
]
</script>

<template>
  <div class="theme-toggle" role="group" aria-label="外观主题">
    <el-tooltip
      v-for="opt in options"
      :key="opt.value"
      :content="opt.label"
      placement="bottom"
      :show-after="0"
      :hide-after="0"
    >
      <button
        type="button"
        :class="{ on: mode === opt.value }"
        :aria-label="opt.label"
        :aria-pressed="mode === opt.value"
        @click="select(opt.value)"
      >
        <el-icon><component :is="opt.icon" /></el-icon>
      </button>
    </el-tooltip>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Monitor, Moon, Sunny } from '@element-plus/icons-vue'
import { applyTheme, getStoredTheme } from '../utils/theme'

const options = [
  { value: 'light', label: '浅色', icon: Sunny },
  { value: 'dark', label: '深色', icon: Moon },
  { value: 'auto', label: '跟随系统', icon: Monitor }
]

const mode = ref(getStoredTheme())

function select(value) {
  mode.value = value
  applyTheme(value)
}
</script>

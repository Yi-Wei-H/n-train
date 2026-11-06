<template>
  <van-popup :show="show" position="bottom" round @update:show="(v) => $emit('update:show', v)">
    <div class="sheet">
      <div class="sheet-grab" />
      <div class="sheet-title">{{ title }}</div>
      <div class="input-wrap">
        <AppIcon name="search" />
        <input v-model="keyword" class="input" placeholder="输入站名或拼音" />
      </div>
      <div class="sheet-options" style="margin-top: 12px">
        <button
          v-for="s in filtered"
          :key="s.code"
          type="button"
          class="sheet-opt"
          @click="$emit('select', s)"
        >
          <span>{{ s.name }}</span>
          <span class="opt-code mono">{{ s.code }}</span>
        </button>
        <div v-if="!filtered.length" class="empty">
          <AppIcon name="search" size="xl" />
          <span>没有匹配的车站</span>
        </div>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  show: { type: Boolean, default: false },
  stations: { type: Array, default: () => [] },
  title: { type: String, default: '选择车站' }
})
defineEmits(['update:show', 'select'])

const keyword = ref('')
watch(() => props.show, (v) => { if (v) keyword.value = '' })

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return props.stations
  return props.stations.filter(
    (s) => s.name.includes(kw) || s.code.toLowerCase().includes(kw) || (s.pinyin || '').toLowerCase().includes(kw)
  )
})
</script>

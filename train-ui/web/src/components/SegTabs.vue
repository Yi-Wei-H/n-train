<template>
  <div ref="root" class="seg block" role="tablist">
    <span ref="thumb" class="thumb" :style="thumbStyle" aria-hidden="true" />
    <button
      v-for="tab in tabs"
      :key="tab.value"
      ref="btnEls"
      type="button"
      role="tab"
      :aria-selected="tab.value === modelValue"
      :aria-pressed="tab.value === modelValue"
      @click="select(tab.value)"
    >{{ tab.label }}</button>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  tabs: { type: Array, required: true },
  modelValue: { type: [String, Number], required: true }
})
const emit = defineEmits(['update:modelValue', 'change'])

const root = ref(null)
const thumb = ref(null)
const btnEls = ref([])
const thumbStyle = ref({})
let observer = null

function activeIndex() {
  return Math.max(0, props.tabs.findIndex((tab) => tab.value === props.modelValue))
}

function measure() {
  const btn = btnEls.value[activeIndex()]
  if (!btn) {
    return
  }
  thumbStyle.value = {
    width: btn.offsetWidth + 'px',
    height: btn.offsetHeight + 'px',
    transform: `translate(${btn.offsetLeft}px, ${btn.offsetTop}px)`
  }
}

function select(value) {
  if (value === props.modelValue) {
    return
  }
  emit('update:modelValue', value)
  emit('change', value)
}

watch(() => props.modelValue, () => nextTick(measure))
watch(() => props.tabs.length, () => nextTick(measure))

onMounted(() => {
  nextTick(() => {
    measure()
    requestAnimationFrame(() => thumb.value?.classList.add('anim'))
  })
  if (window.ResizeObserver && root.value) {
    observer = new ResizeObserver(measure)
    observer.observe(root.value)
  }
})

onBeforeUnmount(() => observer?.disconnect())
</script>

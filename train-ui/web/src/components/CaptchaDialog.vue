<template>
  <van-dialog
    :show="show"
    title="请输入图形验证码"
    show-cancel-button
    @cancel="$emit('update:show', false)"
    @confirm="onConfirm"
    @update:show="(v) => $emit('update:show', v)"
  >
    <div class="captcha-body">
      <div class="inline-field">
        <input v-model="code" class="input" placeholder="验证码" maxlength="4" />
        <button type="button" class="captcha-img" aria-label="刷新验证码" @click="refresh">
          <img v-if="captcha.imageBase64" :src="captcha.imageBase64" alt="验证码" />
          <span v-else class="muted">获取</span>
        </button>
      </div>
    </div>
  </van-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import request from '../utils/request'

const props = defineProps({ show: { type: Boolean, default: false } })
const emit = defineEmits(['update:show', 'verified'])

const captcha = ref({})
const code = ref('')

async function refresh() {
  captcha.value = await request.get('/captcha')
  code.value = ''
}

watch(() => props.show, (v) => { if (v) refresh() })

function onConfirm() {
  emit('verified', { captchaKey: captcha.value.captchaKey, captchaCode: code.value })
  emit('update:show', false)
}
</script>

<style scoped>
.captcha-body {
  padding: 4px 20px 20px;
}

.captcha-img {
  flex-shrink: 0;
  width: 104px;
  height: 44px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-subtle);
  overflow: hidden;
  cursor: pointer;
}

.captcha-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>

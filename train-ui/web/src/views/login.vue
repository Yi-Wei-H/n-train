<template>
  <div class="login-page">
    <ThemeToggle class="login-theme" />

    <header class="login-hero">
      <div class="logo"><img :src="logoBlue" alt="" /></div>
      <div class="app-name">铁旅通票务系统</div>
      <div class="app-slogan">安全出行 · 一路随行</div>
    </header>

    <main class="login-main">
      <section class="card login-card">
        <SegTabs v-model="mode" :tabs="modeTabs" @change="onModeChange" />

        <div class="login-fields">
          <label class="field">
            <span class="field-label">手机号</span>
            <input v-model="phone" class="input" type="tel" maxlength="11" placeholder="请输入11位手机号" />
          </label>

          <label v-if="mode === 'password'" class="field">
            <span class="field-label">密码</span>
            <input v-model="password" class="input" type="password" placeholder="请输入密码" />
          </label>

          <template v-else>
            <div class="field">
              <span class="field-label">图形验证码</span>
              <div class="inline-field">
                <input v-model="captchaCode" class="input" placeholder="先输图形码再发短信" maxlength="4" />
                <button type="button" class="captcha-img" aria-label="刷新图形验证码" @click="refreshCaptcha">
                  <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
                  <span v-else class="muted">获取</span>
                </button>
              </div>
            </div>
            <div class="field">
              <span class="field-label">短信验证码</span>
              <div class="inline-field">
                <input v-model="smsCode" class="input" type="tel" maxlength="6" placeholder="6 位验证码" />
                <button
                  type="button"
                  class="btn sm neutral send-code"
                  :disabled="countdown > 0"
                  @click="sendCode"
                >{{ countdown > 0 ? `${countdown}s 后重发` : '发送验证码' }}</button>
              </div>
            </div>
          </template>
        </div>

        <button class="btn solid touch block login-submit" :disabled="loading" @click="submit">
          {{ loading ? '登录中…' : mode === 'password' ? '登录' : '验证并登录' }}
        </button>
        <button type="button" class="btn quiet block register-entry" @click="$router.push('/register')">
          注册新账号
        </button>
      </section>

      <p class="footer-tip">登录即代表同意《服务协议》与《隐私政策》</p>
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast } from '../utils/toast'
import request from '../utils/request'
import { encrypt } from '../utils/aes'
import { useUserStore } from '../stores/user'
import SegTabs from '../components/SegTabs.vue'
import ThemeToggle from '../components/ThemeToggle.vue'
import logoBlue from '../assets/logo/logo-blue-256.png'

const router = useRouter()
const route = useRoute()
const user = useUserStore()

const modeTabs = [
  { label: '密码登录', value: 'password' },
  { label: '短信登录', value: 'sms' }
]
const mode = ref('password')
const phone = ref('')
const password = ref('')
const captchaImage = ref('')
const captchaKey = ref('')
const captchaCode = ref('')
const smsCode = ref('')
const countdown = ref(0)
const loading = ref(false)

function onModeChange() {
  if (mode.value === 'sms' && !captchaImage.value) {
    refreshCaptcha()
  }
}

async function refreshCaptcha() {
  const data = await request.get('/captcha')
  captchaImage.value = data.imageBase64
  captchaKey.value = data.captchaKey
  captchaCode.value = ''
}

async function sendCode() {
  if (!/^1\d{10}$/.test(phone.value)) return showToast('手机号格式不正确')
  if (!captchaCode.value) return showToast('请先输入图形验证码')
  try {
    const code = await request.post('/user/send-code', {
      phone: encrypt(phone.value),
      captchaKey: captchaKey.value,
      captchaCode: captchaCode.value
    })
    // 模拟短信通道：后端回显验证码（接真实短信后不再返回）
    showToast({ message: code ? `模拟短信验证码：${code}` : '验证码已发送，请查收短信', duration: 5000 })
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } finally {
    // 图形码一次一用，后端校验时无论成败都已消费，必须换新图
    refreshCaptcha()
  }
}

async function submit() {
  if (!/^1\d{10}$/.test(phone.value)) return showToast('手机号格式不正确')
  loading.value = true
  try {
    if (mode.value === 'password') {
      if (!password.value) {
        loading.value = false
        return showToast('请输入密码')
      }
      await user.login(phone.value, password.value)
    } else {
      if (!smsCode.value) {
        loading.value = false
        return showToast('请输入短信验证码')
      }
      await user.smsLogin(phone.value, smsCode.value)
    }
    showToast({ message: '登录成功', type: 'success' })
    router.replace(route.query.redirect || '/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--bg-page);
}

.login-theme {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 5;
  background: rgba(255, 255, 255, .14);
  border-color: rgba(255, 255, 255, .28);
}

.login-theme :deep(button) {
  color: rgba(255, 255, 255, .85);
}

.login-theme :deep(button.on) {
  background: rgba(255, 255, 255, .24);
  color: #fff;
  box-shadow: none;
}

.login-hero {
  padding: 44px 0 48px;
  text-align: center;
  color: #fff;
  background: linear-gradient(180deg, var(--brand-1), var(--brand-2));
}

.logo {
  width: 72px;
  height: 72px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
}

.logo img {
  width: 60px;
  height: 60px;
  object-fit: contain;
}

.app-name {
  font-size: 18px;
  line-height: 26px;
  font-weight: 600;
  letter-spacing: 1px;
}

.app-slogan {
  margin-top: 4px;
  font-size: 13px;
  line-height: 20px;
  opacity: .85;
}

.login-main {
  position: relative;
  margin-top: -28px;
  padding: 0 16px;
}

.login-card {
  padding: 16px;
}

.login-fields {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 16px;
}

.login-submit {
  margin-top: 20px;
}

.register-entry {
  margin-top: 10px;
  color: var(--action-text);
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

.send-code {
  flex-shrink: 0;
  width: 104px;
  padding: 0 8px;
}

.footer-tip {
  padding: 24px 0 calc(20px + env(safe-area-inset-bottom));
  text-align: center;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
</style>

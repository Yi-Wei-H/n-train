<template>
  <div class="page page-plain register-page">
    <PageNav title="注册" />

    <div class="register-body">
      <section class="card register-card">
        <p class="caption register-intro">实名注册后，手机号与身份证信息不可修改</p>

        <div class="register-fields">
          <label class="field">
            <span class="field-label">手机号</span>
            <input v-model="phone" class="input" type="tel" maxlength="11" placeholder="11 位手机号" />
          </label>
          <label class="field">
            <span class="field-label">身份证</span>
            <input v-model="idCardNo" class="input" maxlength="18" placeholder="18 位身份证号" />
            <span class="field-hint">用于实名购票，注册后不可修改</span>
          </label>
          <label class="field">
            <span class="field-label">密码</span>
            <input v-model="password" class="input" type="password" placeholder="设置登录密码" />
          </label>
          <div class="field">
            <span class="field-label">图形验证码</span>
            <div class="inline-field">
              <input v-model="captchaCode" class="input" placeholder="请输入图形验证码" maxlength="4" />
              <button type="button" class="captcha-img" aria-label="刷新图形验证码" @click="refreshCaptcha">
                <img v-if="captchaImage" :src="captchaImage" alt="验证码" />
                <span v-else class="muted">获取</span>
              </button>
            </div>
          </div>
        </div>

        <button class="btn solid touch block register-submit" :disabled="loading" @click="submit">
          {{ loading ? '注册中…' : '注册' }}
        </button>
      </section>

      <p class="register-tip">注册即代表同意《服务协议》与《隐私政策》</p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from '../utils/toast'
import request from '../utils/request'
import { rsaEncrypt } from '../utils/rsa'
import { encrypt } from '../utils/aes'
import { useUserStore } from '../stores/user'
import PageNav from '../components/PageNav.vue'

const router = useRouter()
const user = useUserStore()
const phone = ref('')
const idCardNo = ref('')
const password = ref('')
const captchaCode = ref('')
const captchaImage = ref('')
const captchaKey = ref('')
const loading = ref(false)

async function refreshCaptcha() {
  const data = await request.get('/captcha')
  captchaImage.value = data.imageBase64
  captchaKey.value = data.captchaKey
  captchaCode.value = ''
}

async function submit() {
  if (!/^1\d{10}$/.test(phone.value)) return showToast('手机号格式不正确')
  if (!/^\d{17}[\dXx]$/.test(idCardNo.value)) return showToast('身份证号格式不正确')
  if (!password.value) return showToast('请设置密码')
  if (!captchaCode.value) return showToast('请输入验证码')
  loading.value = true
  try {
    // 手机号/身份证 AES 传输加密；密码 RSA 短时效公钥加密
    const { keyId, cipher } = await rsaEncrypt(password.value)
    await request.post('/user/register', {
      phone: encrypt(phone.value),
      idCardNo: encrypt(idCardNo.value),
      keyId,
      cipher,
      captchaKey: captchaKey.value,
      captchaCode: captchaCode.value
    })
    showToast({ message: '注册成功，已自动登录', type: 'success' })
    await user.login(phone.value, password.value)
    router.replace('/')
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<style scoped>
.register-body {
  padding: 12px 16px 0;
}

.register-card {
  padding: 16px;
}

.register-intro {
  margin: 0 0 16px;
}

.register-fields {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.register-submit {
  margin-top: 20px;
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

.register-tip {
  padding: 20px 0 calc(16px + env(safe-area-inset-bottom));
  text-align: center;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-3);
}
</style>

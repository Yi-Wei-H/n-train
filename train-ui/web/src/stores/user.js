import { defineStore } from 'pinia'
import request from '../utils/request'
import { rsaEncrypt } from '../utils/rsa'
import { encrypt } from '../utils/aes'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('ntrain-web-token') || '',
    member: JSON.parse(localStorage.getItem('ntrain-web-member') || 'null')
  }),
  actions: {
    /** 密码登录：密码走 RSA 短时效公钥，手机号走 AES 传输加密 */
    async login(phone, password) {
      const { keyId, cipher } = await rsaEncrypt(password)
      const data = await request.post('/user/login', { phone: encrypt(phone), keyId, cipher })
      this.token = data.token
      this.member = data.member
      localStorage.setItem('ntrain-web-token', data.token)
      localStorage.setItem('ntrain-web-member', JSON.stringify(data.member || null))
      return data
    },
    /** 短信验证码登录 */
    async smsLogin(phone, smsCode) {
      const data = await request.post('/user/login', { phone: encrypt(phone), smsCode })
      this.token = data.token
      this.member = data.member
      localStorage.setItem('ntrain-web-token', data.token)
      localStorage.setItem('ntrain-web-member', JSON.stringify(data.member || null))
      return data
    },
    setToken(token) {
      this.token = token
      localStorage.setItem('ntrain-web-token', token)
    },
    /** 以 localStorage 为准回填登录态：401 拦截器直接清 localStorage，store 内可能残留旧值 */
    syncToken() {
      this.token = localStorage.getItem('ntrain-web-token') || ''
      if (!this.token) {
        this.member = null
      }
    },
    async me() {
      this.member = await request.get('/user/me')
      localStorage.setItem('ntrain-web-member', JSON.stringify(this.member || null))
      return this.member
    },
    logout() {
      this.token = ''
      this.member = null
      localStorage.removeItem('ntrain-web-token')
      localStorage.removeItem('ntrain-web-member')
    }
  }
})

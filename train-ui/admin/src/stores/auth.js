import { defineStore } from 'pinia'
import request from '../utils/request'
import { rsaEncrypt } from '../utils/rsa'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('ntrain-admin-token') || '',
    username: localStorage.getItem('ntrain-admin-username') || '',
    nickname: localStorage.getItem('ntrain-admin-nickname') || '',
    superAdmin: localStorage.getItem('ntrain-admin-super') === '1'
  }),
  actions: {
    async login(username, password) {
      const { keyId, cipher } = await rsaEncrypt(password)
      const data = await request.post('/auth/login', { username, keyId, cipher })
      this.token = data.token
      this.username = username
      this.nickname = data.nickname
      this.superAdmin = data.superAdmin
      localStorage.setItem('ntrain-admin-token', data.token)
      localStorage.setItem('ntrain-admin-username', username)
      localStorage.setItem('ntrain-admin-nickname', data.nickname || '')
      localStorage.setItem('ntrain-admin-super', data.superAdmin ? '1' : '0')
      return data
    },
    async me() {
      const data = await request.get('/auth/me')
      if (data) {
        this.username = data.username || this.username
        this.nickname = data.nickname || this.nickname
        if (data.userType) this.superAdmin = data.userType === 'S'
        localStorage.setItem('ntrain-admin-username', this.username || '')
        localStorage.setItem('ntrain-admin-nickname', this.nickname || '')
        localStorage.setItem('ntrain-admin-super', this.superAdmin ? '1' : '0')
      }
      return data
    },
    async logout() {
      try {
        await request.post('/auth/logout')
      } catch (e) { /* 无状态登出失败不阻塞 */ }
      localStorage.removeItem('ntrain-admin-token')
      localStorage.removeItem('ntrain-admin-username')
      localStorage.removeItem('ntrain-admin-nickname')
      localStorage.removeItem('ntrain-admin-super')
      this.$reset()
    }
  }
})

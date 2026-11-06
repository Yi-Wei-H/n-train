import axios from 'axios'
import { showToast } from './toast'
import { deepDecrypt } from './aes'
import { getDeviceId } from './device'

/**
 * 各服务域 axios 实例工厂：token、X-Device-Id、Result 解包、401 跳登录全仓统一口径。
 * noToastCodes 内的业务码不弹 toast，由页面自行处理（如 member 下单验证码 41006）。
 */
export function createRequest(baseURL, { noToastCodes = [] } = {}) {
  const instance = axios.create({
    baseURL,
    timeout: 15000
  })

  instance.interceptors.request.use((config) => {
    const token = localStorage.getItem('ntrain-web-token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    const deviceId = getDeviceId()
    if (deviceId) {
      config.headers['X-Device-Id'] = deviceId
    }
    return config
  })

  instance.interceptors.response.use(
    (response) => {
      // 敏感字段（姓名/身份证/手机号）传输加密：ENC: 前缀密文递归解密
      response.data = deepDecrypt(response.data)
      const res = response.data
      if (res.code !== 0) {
        if (!noToastCodes.includes(res.code)) {
          showToast(res.message || '请求失败')
        }
        if (res.code === 40100 || res.code === 40101) {
          localStorage.removeItem('ntrain-web-token')
          localStorage.removeItem('ntrain-web-member')
          window.location.hash = '#/login'
        }
        return Promise.reject(Object.assign(new Error(res.message), { code: res.code, data: res }))
      }
      return res.data
    },
    (error) => {
      if (error.response?.status === 401) {
        localStorage.removeItem('ntrain-web-token')
        localStorage.removeItem('ntrain-web-member')
        window.location.hash = '#/login'
        return Promise.reject(error)
      }
      const body = error.response?.data
      showToast(body?.message || error.message || '网络异常')
      return Promise.reject(Object.assign(error, { code: body?.code }))
    }
  )

  return instance
}

const request = createRequest('/api/member', { noToastCodes: [41006] })

export default request

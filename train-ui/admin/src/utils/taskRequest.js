import axios from 'axios'
import { ElMessage } from 'element-plus'
import { deepDecrypt } from './aes'

/**
 * 定时任务（train-task 服务）专用 axios 实例：
 * 网关路由 /api/task/** → train-task（StripPrefix=2 → /job/**），
 * 与管理台 request.js（/api/admin）分属两个后端服务，故 baseURL 不同。
 */
const request = axios.create({
  baseURL: '/api/task',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('ntrain-admin-token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    // 敏感字段（姓名/身份证/手机号）传输加密：ENC: 前缀密文递归解密
    response.data = deepDecrypt(response.data)
    const res = response.data
    if (res.code !== 0) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 40100 || res.code === 40101) {
        localStorage.removeItem('ntrain-admin-token')
        window.location.hash = '#/login'
      }
      return Promise.reject(new Error(res.message))
    }
    return res.data
  },
  (error) => {
    const status = error.response?.status
    const body = error.response?.data
    if (status === 401) {
      localStorage.removeItem('ntrain-admin-token')
      window.location.hash = '#/login'
      return Promise.reject(error)
    }
    ElMessage.error(body?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request

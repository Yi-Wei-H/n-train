import axios from 'axios'
import { ElMessage } from 'element-plus'
import { deepDecrypt } from './aes'

/**
 * 分页字段归一：后端为防雪花 ID 精度丢失把 long 序列化为字符串，
 * total/pageNum/pageSize 也变成字符串；Element Plus 分页只认 number（字符串会被当作未设置、页码恒为 1）。
 * 仅对 PageResult 形态（含 total + list）转换，业务 ID 字符串原样保留。
 */
const normalizePage = (data) => {
  if (data && typeof data === 'object' && !Array.isArray(data)
      && Object.prototype.hasOwnProperty.call(data, 'total')
      && Object.prototype.hasOwnProperty.call(data, 'list')) {
    data.total = Number(data.total) || 0
    data.pageNum = Number(data.pageNum) || 1
    data.pageSize = Number(data.pageSize) || 10
  }
  return data
}

const request = axios.create({
  baseURL: '/api/admin',
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
    return normalizePage(res.data)
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

/** v-perm 指令：无权限时移除按钮（超管 "*" 放行） */
export const permButton = {
  mounted(el, binding) {
    const perms = JSON.parse(localStorage.getItem('ntrain-admin-perms') || '[]')
    const need = binding.value
    if (!need) return
    if (!perms.includes('*') && !perms.includes(need)) {
      el.parentNode?.removeChild(el)
    }
  }
}

export default request

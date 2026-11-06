import CryptoJS from 'crypto-js'

const RAW_KEY = import.meta.env.VITE_AES_KEY
if (!RAW_KEY || RAW_KEY.length !== 16) {
  throw new Error('[aes] 未配置 VITE_AES_KEY（必须 16 个字符），请参考 .env.example 创建 .env 后重启或重新构建')
}

const KEY = CryptoJS.enc.Utf8.parse(RAW_KEY)
const PREFIX = 'ENC:'
const OPTIONS = { mode: CryptoJS.mode.ECB, padding: CryptoJS.pad.Pkcs7 }

/** 敏感字段（姓名/身份证/手机号）传输加密，与后端 AesUtil 约定一致 */
export const encrypt = (plain) => {
  if (!plain || plain.startsWith(PREFIX)) {
    return plain
  }
  return PREFIX + CryptoJS.AES.encrypt(plain, KEY, OPTIONS).toString()
}

export const decrypt = (value) => {
  if (!value || !value.startsWith(PREFIX)) {
    return value
  }
  const bytes = CryptoJS.AES.decrypt(value.substring(PREFIX.length), KEY, OPTIONS)
  return bytes.toString(CryptoJS.enc.Utf8)
}

/** 响应体递归解密：ENC: 开头的字符串全部还原明文（axios 拦截器统一调用） */
export const deepDecrypt = (data) => {
  if (typeof data === 'string') {
    return decrypt(data)
  }
  if (Array.isArray(data)) {
    return data.map(deepDecrypt)
  }
  if (data && typeof data === 'object') {
    for (const key in data) {
      data[key] = deepDecrypt(data[key])
    }
    return data
  }
  return data
}

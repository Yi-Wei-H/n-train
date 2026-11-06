/** 敏感信息展示遮掩（与后端 DesensitizeUtil 规则一致） */

/** 手机号：138****8000 */
export const maskPhone = (phone) => {
  if (!phone || phone.length < 7) return phone || ''
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

/** 身份证：保留前 3 后 4 */
export const maskIdCard = (id) => {
  if (!id || id.length < 8) return id || ''
  return id.slice(0, 3) + '***********' + id.slice(-4)
}

/** 姓名：张*丰 */
export const maskName = (name) => {
  if (!name || name.length <= 1) return name || ''
  if (name.length === 2) return name.charAt(0) + '*'
  return name.charAt(0) + '*'.repeat(name.length - 2) + name.charAt(name.length - 1)
}

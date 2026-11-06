/**
 * agent 域枚举与共用常量：DDL 口径为 1/2（与 admin 系统域的 0/1 不同），
 * 页面一律走本文件映射，不在模板里裸写数字。
 */

export const MODEL_TYPES = [
  { value: 1, label: '文字' },
  { value: 2, label: '向量' }
]

/** Gemini 暂未实现客户端（服务端保存即拒绝），不列入可选项 */
export const API_FORMATS = [
  { value: 1, label: 'OpenAI 兼容-chat' },
  { value: 2, label: 'OpenAI 兼容-Responses' },
  { value: 3, label: 'Anthropic(messages)' }
]

export const AUTH_TYPES = [
  { value: 1, label: 'Bearer 头' },
  { value: 2, label: 'Key 头直传' },
  { value: 3, label: '查询参数' }
]

export const ON_OFF = [
  { value: 1, label: '启用' },
  { value: 2, label: '停用' }
]

export const CONV_STATUS = [
  { value: 1, label: '正常', type: 'primary' },
  { value: 2, label: '归档', type: 'info' }
]

export const TITLE_STATUS = [
  { value: 0, label: '未触发', type: 'info' },
  { value: 1, label: '生成中', type: 'warning' },
  { value: 2, label: '成功', type: 'success' },
  { value: 3, label: '失败', type: 'danger' }
]

export const CALL_STATUS = [
  { value: 1, label: '成功', type: 'success' },
  { value: 2, label: '失败', type: 'danger' }
]

export const MSG_ROLES = [
  { value: 'user', label: 'user', type: 'primary' },
  { value: 'assistant', label: 'assistant', type: 'success' },
  { value: 'system', label: 'system', type: 'info' }
]

/** 常用卡片图标标识（H5 端维护「标识 → 图标」映射，未匹配回落 chat-o） */
export const CARD_ICONS = ['ticket', 'search', 'policy', 'buy', 'refund', 'remind', 'calendar', 'station', 'help', 'service']

/** 慢接口（上游 LLM 同步调用）单请求超时：全局 15s 不够 */
export const SLOW_TIMEOUT = 45000

export function labelOf(options, value) {
  const hit = options.find((o) => o.value === value)
  return hit ? hit.label : '—'
}

export function tagOf(options, value) {
  const hit = options.find((o) => o.value === value)
  return hit || { label: '—', type: 'info' }
}

/** 必填字段的基础前端校验（服务端仍会校验）：返回首个缺失项文案，全部通过返回空串 */
export function requireFields(form, fields) {
  for (const [label, value] of fields) {
    if (value === null || value === undefined || String(value).trim() === '') {
      return `请填写${label}`
    }
  }
  return ''
}

/** 模型/预设"默认参数"示例：含思考开关两种业界形态与厂商非标键透传写法（stripThinking：剥离 <think> 思考块） */
export const PARAMS_PLACEHOLDER = `{
  "temperature": 0.7,
  "maxTokens": 4096,
  "thinkingType": "disabled",
  "thinkingBudgetTokens": 4096,
  "reasoningEffort": "low",
  "extraBody": {
    "thinking": { "type": "disabled" }
  },
  "stripThinking": false
}`

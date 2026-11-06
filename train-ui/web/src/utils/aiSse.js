import { getDeviceId } from './device'

/**
 * fetch 版 SSE 消费：EventSource 不支持 POST 与自定义头（Authorization / X-Device-Id），
 * 用 fetch + ReadableStream 手工解析。事件约定：
 * - meta / title / confirmation / done 为命名事件，载荷 JSON；
 * - 增量为默认 message 事件，载荷纯文本，多行 data: 以 \n 拼接还原；
 * - error 载荷为可展示文案（纯文本或含 message 字段的对象）。
 * 推流前失败时服务端返回 application/json 的 Result：按 axios 工厂同款规则解包，
 * code!==0 抛 Error(code/message)；401 清 token 跳登录。
 */
export async function postChatStream({ url, body, signal, onMeta, onDelta, onTitle, onConfirmation, onError, onDone }) {
  const headers = { 'Content-Type': 'application/json', Accept: 'text/event-stream' }
  const token = localStorage.getItem('ntrain-web-token')
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const deviceId = getDeviceId()
  if (deviceId) {
    headers['X-Device-Id'] = deviceId
  }

  let response
  try {
    response = await fetch(url, { method: 'POST', headers, body: JSON.stringify(body), signal })
  } catch (e) {
    if (e.name === 'AbortError') {
      throw e
    }
    throw new Error('网络异常，请稍后重试')
  }

  if (response.status === 401) {
    localStorage.removeItem('ntrain-web-token')
    window.location.hash = '#/login'
    throw new Error('登录已过期')
  }

  const contentType = response.headers.get('content-type') || ''
  if (!response.ok || contentType.includes('application/json')) {
    let result = null
    try {
      result = await response.json()
    } catch {
      // 非 JSON 错误体：按状态码兜底
    }
    if (result && result.code === 0) {
      return result.data
    }
    const error = new Error(result?.message || `请求失败（${response.status}）`)
    error.code = result?.code
    throw error
  }

  await readSseStream(response, { onMeta, onDelta, onTitle, onConfirmation, onError, onDone })
}

async function readSseStream(response, handlers) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let eventName = ''
  let dataLines = []

  const dispatch = () => {
    if (!dataLines.length && !eventName) {
      return
    }
    const data = dataLines.join('\n')
    const name = eventName || 'message'
    eventName = ''
    dataLines = []
    routeEvent(name, data, handlers)
  }

  const consumeLine = (line) => {
    if (line === '') {
      dispatch()
      return
    }
    if (line.startsWith(':')) {
      return
    }
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    if (field === 'event') {
      eventName = value
    } else if (field === 'data') {
      dataLines.push(value)
    }
  }

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    let nl
    while ((nl = buffer.indexOf('\n')) !== -1) {
      const line = buffer.slice(0, nl).replace(/\r$/, '')
      buffer = buffer.slice(nl + 1)
      consumeLine(line)
    }
  }
  buffer += decoder.decode()
  if (buffer) {
    consumeLine(buffer.replace(/\r$/, ''))
  }
  dispatch()
}

function routeEvent(name, data, { onMeta, onDelta, onTitle, onConfirmation, onError, onDone }) {
  const json = parseJson(data)
  switch (name) {
    case 'meta':
      onMeta?.(json)
      break
    case 'title':
      onTitle?.(json)
      break
    case 'confirmation':
      onConfirmation?.(json)
      break
    case 'error':
      onError?.(typeof json === 'string' ? json : json?.message || data)
      break
    case 'done':
      onDone?.(json)
      break
    default:
      onDelta?.(data)
  }
}

function parseJson(text) {
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

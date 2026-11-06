/* 轻量 Toast：底部卡片、成功勾选图标、失败警示图标；内容走 textContent 防注入 */
const ICONS = {
  info: '<svg class="i" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/></svg>',
  success: '<svg class="i" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/></svg>',
  error: '<svg class="i" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/></svg>'
}

// 同类型同文案提示在窗口期内只保留一条：页面并发多个请求同时失败时避免重复弹出
const DEDUPE_WINDOW_MS = 1000
let lastToast = { message: '', type: '', at: 0 }

function root() {
  let el = document.getElementById('app-toaster')
  if (!el) {
    el = document.createElement('div')
    el.id = 'app-toaster'
    el.setAttribute('aria-live', 'polite')
    document.body.appendChild(el)
  }
  return el
}

export function showToast(options) {
  const opt = typeof options === 'string' ? { message: options } : { ...options }
  const message = opt.message == null ? '' : String(opt.message)
  if (!message) {
    return
  }
  const type = opt.type === 'success' ? 'success' : opt.type === 'error' || opt.type === 'fail' ? 'error' : 'info'

  const now = Date.now()
  if (type === lastToast.type && message === lastToast.message && now - lastToast.at < DEDUPE_WINDOW_MS) {
    return
  }
  lastToast = { message, type, at: now }

  const card = document.createElement('div')
  card.className = 'toast-card' + (type === 'success' ? ' ok' : type === 'error' ? ' err' : '')
  card.innerHTML = ICONS[type]
  const text = document.createElement('span')
  text.className = 'grow'
  text.textContent = message
  card.appendChild(text)

  let removed = false
  const dismiss = () => {
    if (removed) {
      return
    }
    removed = true
    card.classList.add('leaving')
    setTimeout(() => card.remove(), 160)
  }

  const close = document.createElement('button')
  close.type = 'button'
  close.className = 'toast-close'
  close.setAttribute('aria-label', '关闭提示')
  close.innerHTML = '<svg class="i" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.25" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>'
  close.addEventListener('click', dismiss)
  card.appendChild(close)

  root().appendChild(card)

  const duration = opt.duration == null ? 2000 : Math.min(opt.duration, 5000)
  setTimeout(dismiss, duration)
}

export function showSuccessToast(options) {
  const opt = typeof options === 'string' ? { message: options } : { ...options }
  showToast({ ...opt, type: 'success' })
}

export function showFailToast(options) {
  const opt = typeof options === 'string' ? { message: options } : { ...options }
  showToast({ ...opt, type: 'error' })
}

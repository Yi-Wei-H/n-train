const THEME_KEY = 'ntrain-admin-theme'

export const THEMES = ['light', 'dark', 'auto']

export function getStoredTheme() {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    return THEMES.includes(saved) ? saved : 'auto'
  } catch (e) {
    return 'auto'
  }
}

/** 应用主题；auto 由 CSS 的 prefers-color-scheme 接管，手动选择优先于系统 */
export function applyTheme(mode, { persist = true } = {}) {
  const next = THEMES.includes(mode) ? mode : 'auto'
  const root = document.documentElement

  root.classList.add('theme-switching')
  root.setAttribute('data-theme', next)

  if (persist) {
    try {
      localStorage.setItem(THEME_KEY, next)
    } catch (e) {
      /* 隐私模式下忽略 */
    }
  }

  requestAnimationFrame(() => {
    requestAnimationFrame(() => root.classList.remove('theme-switching'))
  })
}

export function initTheme() {
  applyTheme(getStoredTheme(), { persist: false })
}

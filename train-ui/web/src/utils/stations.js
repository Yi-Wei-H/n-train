import request from './request'

/**
 * 车站电报码 → 名称映射（本地缓存 24 小时）。
 * 后端各 DTO 只带电报码，展示层统一经此转换为站名。
 */
const CACHE_KEY = 'ntrain-web-stations'
const TTL = 24 * 60 * 60 * 1000

let cached = null
let pending = null

function readLocal() {
  try {
    const saved = JSON.parse(localStorage.getItem(CACHE_KEY) || 'null')
    if (saved && Date.now() - saved.t < TTL && Array.isArray(saved.list)) {
      return saved.list
    }
  } catch { /* 缓存损坏则重取 */ }
  return null
}

/** 返回 { 电报码: 站名 } 映射 */
export async function loadStationMap() {
  if (cached) return cached
  const local = readLocal()
  if (local) {
    cached = Object.fromEntries(local.map((s) => [s.code, s.name]))
    return cached
  }
  if (!pending) {
    pending = request.get('/ticket/stations').then((list) => {
      localStorage.setItem(CACHE_KEY, JSON.stringify({ t: Date.now(), list }))
      cached = Object.fromEntries(list.map((s) => [s.code, s.name]))
      pending = null
      return cached
    })
  }
  return pending
}

/** 取站名；未命中时回退原码（缓存未就绪/站点缺失时不显示空白） */
export function stationName(map, code) {
  return (map && map[code]) || code
}

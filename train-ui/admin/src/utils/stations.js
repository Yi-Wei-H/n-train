import request from './request'

/**
 * 车站电报码 → 名称映射（模块级缓存，会话内只请求一次）。
 * 管理端各列表的站码字段展示统一经此转换。
 */
let cached = null
let pending = null

/** 返回 { 电报码: 站名 } 映射 */
export async function loadStationMap() {
  if (cached) return cached
  if (!pending) {
    pending = request.get('/station/list').then((list) => {
      cached = Object.fromEntries(list.map((s) => [s.code, s.name]))
      return cached
    })
  }
  return pending
}

/** 取站名；未命中时回退原码（缓存未就绪/站点缺失时不显示空白） */
export function stationName(map, code) {
  return (map && map[code]) || code
}

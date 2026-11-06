import { reactive } from 'vue'

/**
 * 行内开关的乐观更新。
 * 点击后立即把 row.status 切到目标值，请求成功保持，失败回滚；
 * 同一行请求未结束前不再响应点击。
 *
 * @param {(row, enabled) => Promise} commit 发送业务请求
 * @param {object} options
 * @param {string|Function} options.key 行主键，默认 id
 * @param {number} options.on 开启状态值，默认 1
 * @param {number} options.off 关闭状态值，默认 0
 * @param {(row, enabled) => void} options.onSuccess 成功后的联动处理
 */
export function useOptimisticSwitch(commit, { key = 'id', on = 1, off = 0, onSuccess } = {}) {
  const pending = reactive(new Set())
  const keyOf = typeof key === 'function' ? key : (row) => row[key]

  function isPending(row) {
    return pending.has(keyOf(row))
  }

  async function toggle(row, enabled) {
    const id = keyOf(row)
    if (pending.has(id)) return
    const previous = row.status
    row.status = enabled ? on : off
    pending.add(id)
    let succeeded = false
    try {
      await commit(row, enabled)
      succeeded = true
    } catch (e) {
      row.status = previous
    } finally {
      pending.delete(id)
    }
    if (succeeded) onSuccess?.(row, enabled)
  }

  return { isPending, toggle }
}

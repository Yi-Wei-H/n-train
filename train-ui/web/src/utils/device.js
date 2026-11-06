import FingerprintJS from '@fingerprintjs/fingerprintjs'

const KEY = 'ntrain-web-device'

let promise = null
let deviceId = localStorage.getItem(KEY) || ''

/**
 * 设备指纹（FingerprintJS 开源版）：应用启动即后台计算，
 * 就绪前拦截器不带 X-Device-Id（服务端 DEVICE 维度限流自动回退 IP）。
 */
export function initDeviceId() {
  if (!promise) {
    promise = FingerprintJS.load()
      .then((fp) => fp.get())
      .then((result) => {
        deviceId = result.visitorId
        localStorage.setItem(KEY, deviceId)
        return deviceId
      })
      .catch(() => '') // 采集失败不阻塞业务，仅丢失设备维度限流
  }
  return promise
}

export function getDeviceId() {
  return deviceId
}

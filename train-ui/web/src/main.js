import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import './styles/tokens.css'
import './styles/vant.css'
import './styles/app.css'
import App from './App.vue'
import router from './router'
import { initDeviceId } from './utils/device'
import { initTheme } from './utils/theme'

// 启动即后台计算设备指纹，就绪后随请求携带 X-Device-Id
initDeviceId()
// 主题：浅色 / 深色 / 跟随系统，读取本地存储
initTheme()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(Vant)
app.mount('#app')

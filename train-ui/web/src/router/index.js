import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '../stores/user'
import { setPageTransition } from '../utils/pageTransition'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/login.vue'), meta: { public: true } },
    { path: '/register', name: 'register', component: () => import('../views/register.vue'), meta: { public: true } },
    { path: '/', name: 'home', component: () => import('../views/home.vue'), meta: { tab: true, title: '首页' } },
    { path: '/date', name: 'datePicker', component: () => import('../views/datePicker.vue'), meta: { title: '选择出发日期' } },
    { path: '/ai', name: 'ai', component: () => import('../views/ai.vue'), meta: { tab: true, title: '智能问询' } },
    { path: '/booking', name: 'booking', component: () => import('../views/booking.vue') },
    { path: '/orders', name: 'orders', component: () => import('../views/orders.vue') },
    { path: '/orders/:id', name: 'orderDetail', component: () => import('../views/orderDetail.vue') },
    { path: '/my-tickets', name: 'myTickets', component: () => import('../views/myTickets.vue'), meta: { tab: true, title: '车票' } },
    { path: '/wallet', name: 'wallet', component: () => import('../views/wallet.vue') },
    { path: '/passengers', name: 'passengers', component: () => import('../views/passengers.vue') },
    { path: '/me', name: 'me', component: () => import('../views/me.vue'), meta: { tab: true, title: '我的' } }
  ]
})

/** 令牌是否已过期：解析 JWT exp（秒）；结构异常按无效处理，避免残留脏值放进业务页 */
function isTokenExpired(token) {
  try {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const padded = part + '='.repeat((4 - (part.length % 4)) % 4)
    const payload = JSON.parse(atob(padded))
    return payload.exp ? payload.exp * 1000 <= Date.now() : false
  } catch (e) {
    return true
  }
}

function loginRedirect(to) {
  return { path: '/login', query: to.fullPath && to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
}

/** 已校验通过的标记：只在每次页面加载后校验一次，避免每次路由跳转都请求 */
let authVerified = false

// 移动端入口：启动即检查登录态——未登录一律先进登录页；
// 已登录用户直接进首页；已登录再访问登录页则弹回首页。
router.beforeEach(async (to, from) => {
  // 仅日期选择页参与左右切换：进入时从右推入，返回时反向
  if (to.name === 'datePicker' && from.name !== 'datePicker') {
    setPageTransition('slide-forward')
  } else if (from.name === 'datePicker' && to.name !== 'datePicker') {
    setPageTransition('slide-back')
  } else {
    setPageTransition('')
  }

  const user = useUserStore()
  user.syncToken()
  const authed = !!user.token && !isTokenExpired(user.token)

  if (!to.meta.public && !authed) {
    if (user.token) {
      user.logout() // 过期/脏令牌一并清理，避免继续随请求发出
    }
    return loginRedirect(to)
  }
  if (to.name === 'login') {
    return authed ? { path: to.query.redirect || '/' } : true
  }
  // 首次进入业务页做一次服务端校验：令牌未过期但已失效（改密/被禁用/换签名密钥）同样回登录页；
  // 网络异常不踢人，避免离线时无法使用
  if (authed && !authVerified) {
    try {
      await user.me()
      authVerified = true
    } catch (e) {
      const unauthorized = e?.code === 40100 || e?.code === 40101 || e?.response?.status === 401
      if (unauthorized) {
        user.logout()
        return loginRedirect(to)
      }
      authVerified = true
    }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `铁旅通票务系统 · ${to.meta.title}` : '铁旅通票务系统'
})

export default router

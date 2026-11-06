import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

// 动态路由组件：key 与后端 sys_menu.component 一致（如 system/user）
const modules = import.meta.glob('../views/**/*.vue')

export function buildDynamicRoutes(menus, parentPath = '') {
  const routes = []
  for (const menu of menus || []) {
    if (menu.menuType === 'M') {
      const childPath = menu.path
        ? (menu.path.startsWith('/') ? menu.path : `${parentPath}/${menu.path}`)
        : parentPath
      routes.push(...buildDynamicRoutes(menu.children || [], childPath))
    } else if (menu.menuType === 'C' && menu.path && menu.component) {
      const full = menu.path.startsWith('/') ? menu.path : `${parentPath}/${menu.path}`
      const comp = modules[`../views/${menu.component}.vue`]
      if (comp) {
        routes.push({
          path: full,
          name: full,
          component: comp,
          meta: { title: menu.menuName }
        })
      }
    }
    // F 按钮节点不生成路由
  }
  return routes
}

// 从菜单树收集权限标识（C/F 节点 perms），供 v-perm 按钮级控制
function collectPerms(menus, acc = []) {
  for (const m of menus || []) {
    if (m.perms) acc.push(m.perms)
    collectPerms(m.children, acc)
  }
  return acc
}

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../views/login.vue') },
    {
      path: '/',
      name: 'layout',
      component: () => import('../layout/AdminLayout.vue'),
      redirect: '/welcome',
      children: [
        { path: 'welcome', name: 'welcome', component: () => import('../views/welcome.vue'), meta: { title: '首页' } },
        // 会话消息子页：从会话管理跳入，不进菜单
        {
          path: 'agent/message',
          name: 'agentMessage',
          component: () => import('../views/agent/message.vue'),
          meta: { title: '会话消息' }
        }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.path === '/login') return true
  if (!auth.token) return '/login'
  // 登录后首次进入：拉动态菜单并挂到 layout 下
  if (!window.__ntrainRoutesLoaded) {
    try {
      const request = (await import('../utils/request')).default
      const menus = await request.get('/auth/menus')
      for (const r of buildDynamicRoutes(menus)) {
        router.addRoute('layout', r)
      }
      localStorage.setItem('ntrain-admin-perms', JSON.stringify(collectPerms(menus)))
      window.__ntrainRoutesLoaded = true
      return to.fullPath // 用新注册的路由重新导航
    } catch (e) {
      return '/login'
    }
  }
  return true
})

export default router

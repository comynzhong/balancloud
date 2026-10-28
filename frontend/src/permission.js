import router from './router'
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { getToken } from '@/utils/auth'
import { isHttp } from '@/utils/validate'
import { isRelogin } from '@/utils/request'
import useUserStore from '@/store/modules/user'
import useSettingsStore from '@/store/modules/settings'
import usePermissionStore from '@/store/modules/permission'

NProgress.configure({ showSpinner: false });

const whiteList = ['/login', '/register'];
const CHANGE_PASSWORD_PATH = '/user/change-password';

// 菜单路由的 name 由后端取 path 首字母大写生成（SysMenuService.getRouteName），
// 和常量路由或另一条菜单同名时 Vue Router 会整条替换旧记录，被顶掉的那一页直接 404
// （已发生两次：菜单 170/961 同为 report、平台菜单 995 的 profile 顶掉个人中心）。
// 撞名就改成带路径后缀的名字并告警：两条路由都留着，页面都点得到。
function uniqueRouteNames(route) {
  if (route.name && router.hasRoute(route.name)) {
    const renamed = `${route.name}__${String(route.path).replace(/[^a-zA-Z0-9]+/g, '_')}`
    console.warn(`[router] 菜单 ${route.path} 的路由名 ${route.name} 已存在，改用 ${renamed}`)
    route.name = renamed
  }
  route.children?.forEach(uniqueRouteNames)
  return route
}

router.beforeEach((to, from, next) => {
  NProgress.start()
  if (getToken()) {
    to.meta.title && useSettingsStore().setTitle(to.meta.title)
    /* has token*/
    if (to.path === '/login') {
      next({ path: '/' })
      NProgress.done()
    } else if (whiteList.indexOf(to.path) !== -1) {
      next()
    } else {
      if (useUserStore().roles.length === 0) {
        isRelogin.show = true
        // 判断当前用户是否已拉取完user_info信息
        useUserStore().getInfo().then(() => {
          isRelogin.show = false
          if (useUserStore().pwdResetRequired && to.path !== CHANGE_PASSWORD_PATH) {
            next(CHANGE_PASSWORD_PATH)
            return
          }
          usePermissionStore().generateRoutes().then(accessRoutes => {
            // 根据roles权限生成可访问的路由表
            accessRoutes.forEach(route => {
              if (isHttp(route.path)) {
                return
              }
              try {
                router.addRoute(uniqueRouteNames(route)) // 动态添加可访问路由表
              } catch (e) {
                // 单条菜单配置错误不应导致整页空白
                console.error('[router] 菜单路由注册失败', route.path, e)
              }
            })
            next({ ...to, replace: true }) // hack方法 确保addRoutes已完成
          })
        }).catch(err => {
          useUserStore().logOut().then(() => {
            ElMessage.error(err)
            next({ path: '/' })
          })
        })
      } else if (useUserStore().pwdResetRequired && to.path !== CHANGE_PASSWORD_PATH) {
        next(CHANGE_PASSWORD_PATH)
      } else {
        next()
      }
    }
  } else {
    // 没有token
    if (whiteList.indexOf(to.path) !== -1) {
      // 在免登录白名单，直接进入
      next()
    } else {
      next(`/login?redirect=${to.fullPath}`) // 否则全部重定向到登录页
      NProgress.done()
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

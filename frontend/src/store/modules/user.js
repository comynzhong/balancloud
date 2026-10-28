import { login, logout, getInfo } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'

const useUserStore = defineStore(
  'user',
  {
    state: () => ({
      token: getToken(),
      id: '',
      name: '',
      nickName: '',
      avatar: '',
      roles: [],
      permissions: [],
      pwdResetRequired: false
    }),
    actions: {
      // 登录
      login(userInfo) {
        // 租户编码走请求头，LoginBody 里没有这个字段
        const { tenantCode, ...rest } = userInfo
        const data = {
          ...rest,
          username: userInfo.username.trim()
        }
        return new Promise((resolve, reject) => {
          login(data, tenantCode?.trim()).then(res => {
            setToken(res.token)
            this.token = res.token
            resolve()
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 获取用户信息
      getInfo() {
        return new Promise((resolve, reject) => {
          getInfo().then(res => {
            const user = res.user
            // 平台没有头像存储，avatar 为空时由 el-avatar 回落到昵称首字
            const avatar = user.avatar || ''

            if (res.roles && res.roles.length > 0) { // 验证返回的roles是否是一个非空数组
              this.roles = res.roles
              this.permissions = res.permissions
            } else {
              this.roles = ['ROLE_DEFAULT']
            }
            this.id = user.userId
            this.name = user.userName
            this.nickName = user.nickName || user.userName
            this.avatar = avatar
            this.pwdResetRequired = res.pwdResetRequired === true
            resolve(res)
          }).catch(error => {
            reject(error)
          })
        })
      },
      // 退出系统
      logOut() {
        return new Promise((resolve, reject) => {
          logout(this.token).then(() => {
            this.token = ''
            this.roles = []
            this.permissions = []
            this.pwdResetRequired = false
            removeToken()
            resolve()
          }).catch(error => {
            reject(error)
          })
        })
      }
    }
  })

export default useUserStore

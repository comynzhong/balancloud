import { defineStore } from 'pinia'
import request from '@/utils/request'
import { silentLogin, getToken, clearToken, setDevPhone } from '@/utils/auth'

const USER_KEY = 'bc_app_user'
const USER_TOKEN_KEY = 'bc_app_user_token'

export const useUserStore = defineStore('user', {
  state: () => ({
    user: uni.getStorageSync(USER_KEY) || null,
    ready: false
  }),
  getters: {
    loggedIn: (state) => !!state.user,
    displayName: (state) => (state.user && (state.user.nickname || '充电用户')) || '充电用户'
  },
  actions: {
    async ensureLogin() {
      const token = getToken()
      if (this.user && token && uni.getStorageSync(USER_TOKEN_KEY) === token) return this.user
      if (!token) await silentLogin()
      return this.fetchMe()
    },
    async fetchMe() {
      const data = await request({ url: '/api/app/auth/me' })
      this.user = data
      this.ready = true
      uni.setStorageSync(USER_KEY, data)
      uni.setStorageSync(USER_TOKEN_KEY, getToken())
      return data
    },
    async devLogin(phone) {
      setDevPhone(phone)
      await silentLogin()
      return this.fetchMe()
    },
    async logout() {
      try {
        await request({ url: '/api/app/auth/logout', method: 'POST' })
      } catch (e) {
        // token 已失效时忽略
      }
      clearToken()
      this.user = null
      this.ready = false
      uni.removeStorageSync(USER_KEY)
      uni.reLaunch({ url: '/pages/index/index' })
    }
  }
})

import { defineStore } from 'pinia'
import request from '@/utils/request'

export const useOrderStore = defineStore('order', {
  state: () => ({
    active: null,
    loaded: false
  }),
  actions: {
    async refreshActive() {
      const data = await request({ url: '/api/app/orders/active' })
      this.active = data || null
      this.loaded = true
      return this.active
    },
    clearActive() {
      this.active = null
    }
  }
})

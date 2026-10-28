import { getPublicConfig } from '@/api/balan/runtime'

const DEFAULTS = {
  brand: {
    name: '衡电云控',
    subtitle: '充电桩场站智能运营平台',
    servicePhone: '400-000-0000'
  },
  captchaEnabled: true,
  multiTenantEnabled: false,
  paymentMode: 'mock',
  maintenance: { enabled: false, message: '' },
  miniapp: {
    defaultMaxDurationMinutes: 480,
    defaultChargeLimitAmount: '50',
    maxActiveOrdersPerUser: 1
  }
}

const useRuntimeStore = defineStore('runtime', {
  state: () => ({
    loaded: false,
    brand: { ...DEFAULTS.brand },
    captchaEnabled: DEFAULTS.captchaEnabled,
    multiTenantEnabled: DEFAULTS.multiTenantEnabled,
    paymentMode: DEFAULTS.paymentMode,
    maintenance: { ...DEFAULTS.maintenance },
    miniapp: { ...DEFAULTS.miniapp }
  }),
  actions: {
    async load(force = false) {
      if (this.loaded && !force) return
      try {
        const res = await getPublicConfig()
        this.apply(res.data)
      } catch (e) {
        // 公开配置失败不阻塞登录，沿用本地默认值
      } finally {
        this.loaded = true
      }
    },
    apply(data) {
      if (!data) return
      this.brand = { ...DEFAULTS.brand, ...(data.brand || {}) }
      this.captchaEnabled = data.captchaEnabled !== false
      this.multiTenantEnabled = !!data.multiTenantEnabled
      this.paymentMode = data.paymentMode || 'mock'
      this.maintenance = { ...DEFAULTS.maintenance, ...(data.maintenance || {}) }
      this.miniapp = { ...DEFAULTS.miniapp, ...(data.miniapp || {}) }
    },
    async refresh() {
      return this.load(true)
    }
  }
})

export default useRuntimeStore

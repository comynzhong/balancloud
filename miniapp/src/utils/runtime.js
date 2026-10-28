import { reactive } from 'vue'
import { BASE_URL, DEFAULT_MAX_DURATION, DEFAULT_MAX_AMOUNT, SUPPORT_PHONE } from './config'

const runtimeConfig = reactive({
  loaded: false,
  brand: {
    name: '衡电云控',
    subtitle: '充电桩场站智能运营平台',
    servicePhone: SUPPORT_PHONE
  },
  multiTenantEnabled: false,
  paymentEnabled: true,
  paymentMode: 'mock',
  edition: 'community',
  features: {
    wallet: false,
    benefits: false,
    reservation: false,
    platform: false
  },
  maintenance: {
    enabled: false,
    message: ''
  },
  miniapp: {
    defaultMaxDurationMinutes: DEFAULT_MAX_DURATION,
    defaultChargeLimitAmount: DEFAULT_MAX_AMOUNT,
    maxActiveOrdersPerUser: 1
  }
})

let pending = null

function apply(data) {
  const brand = data.brand || {}
  const maintenance = data.maintenance || {}
  const miniapp = data.miniapp || {}

  runtimeConfig.brand.name = brand.name || runtimeConfig.brand.name
  runtimeConfig.brand.subtitle = brand.subtitle || runtimeConfig.brand.subtitle
  runtimeConfig.brand.servicePhone = brand.servicePhone || runtimeConfig.brand.servicePhone
  runtimeConfig.multiTenantEnabled = !!data.multiTenantEnabled
  runtimeConfig.paymentEnabled = data.paymentEnabled !== false
  runtimeConfig.paymentMode = data.paymentMode || 'mock'
  runtimeConfig.edition = data.edition || runtimeConfig.edition
  const features = data.features || {}
  runtimeConfig.features.wallet = !!features.wallet
  runtimeConfig.features.benefits = !!features.benefits
  runtimeConfig.features.reservation = !!features.reservation
  runtimeConfig.features.platform = !!features.platform
  runtimeConfig.maintenance.enabled = !!maintenance.enabled
  runtimeConfig.maintenance.message = maintenance.message || ''

  const duration = Number(miniapp.defaultMaxDurationMinutes)
  if (Number.isFinite(duration) && duration > 0) {
    runtimeConfig.miniapp.defaultMaxDurationMinutes = duration
  }
  const amount = Number(miniapp.defaultChargeLimitAmount)
  if (Number.isFinite(amount) && amount > 0) {
    runtimeConfig.miniapp.defaultChargeLimitAmount = amount
  }
  const concurrent = Number(miniapp.maxActiveOrdersPerUser)
  if (Number.isFinite(concurrent) && concurrent > 0) {
    runtimeConfig.miniapp.maxActiveOrdersPerUser = concurrent
  }
}

/**
 * 公开配置在启动时拉一次；失败保持内置默认值，不阻塞页面。
 */
export function loadRuntimeConfig(force = false) {
  if (pending) return pending
  if (runtimeConfig.loaded && !force) return Promise.resolve(runtimeConfig)
  pending = new Promise((resolve) => {
    uni.request({
      url: BASE_URL + '/api/v1/public/config',
      method: 'GET',
      timeout: 8000,
      success: (res) => {
        const body = res.data || {}
        if (body.code === 200 && body.data) apply(body.data)
      },
      complete: () => {
        runtimeConfig.loaded = true
        pending = null
        resolve(runtimeConfig)
      }
    })
  })
  return pending
}

export { runtimeConfig }

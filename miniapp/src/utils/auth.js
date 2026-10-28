import { BASE_URL } from './config'

const TOKEN_KEY = 'bc_app_token'
const PHONE_KEY = 'bc_dev_phone'
const TENANT_KEY = 'bc_tenant_code'
export const DEFAULT_DEV_PHONE = '13800000001'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setToken(token) {
  uni.setStorageSync(TOKEN_KEY, token)
}

export function clearToken() {
  uni.removeStorageSync(TOKEN_KEY)
}

/** 运营商编码：只在登录/注册时随 X-Tenant-Code 上送，留空表示默认运营商。 */
export function getTenantCode() {
  return uni.getStorageSync(TENANT_KEY) || ''
}

/** 换运营商必须换会话，否则旧 token 还指着上一个运营商的数据。 */
export function setTenantCode(code) {
  const next = (code || '').trim()
  if (next === getTenantCode()) return
  uni.setStorageSync(TENANT_KEY, next)
  clearToken()
}

export function getDevPhone() {
  return uni.getStorageSync(PHONE_KEY) || DEFAULT_DEV_PHONE
}

export function setDevPhone(phone) {
  uni.setStorageSync(PHONE_KEY, phone)
}

function post(url, data) {
  return new Promise((resolve, reject) => {
    const header = { 'Content-Type': 'application/json' }
    const tenantCode = getTenantCode()
    if (tenantCode) {
      header['X-Tenant-Code'] = tenantCode
    }
    uni.request({
      url: BASE_URL + url,
      method: 'POST',
      data,
      header,
      timeout: 15000,
      success: (res) => resolve(res.data || {}),
      fail: reject
    })
  })
}

/**
 * 入口参数带 ?tenant=xxx 时选定运营商：H5 分享链接、扫码落地页都靠它把 C 端用户落到正确租户。
 * 只在启动时调用一次，之后由本地存储决定，登录请求统一带上 X-Tenant-Code。
 */
export function applyTenantFromUrl() {
  let code = ''
  // #ifdef H5
  code = new URLSearchParams(window.location.search).get('tenant') || ''
  // #endif
  if (!code && typeof uni.getLaunchOptionsSync === 'function') {
    const options = uni.getLaunchOptionsSync() || {}
    code = (options.query && options.query.tenant) || ''
  }
  if (code) {
    setTenantCode(String(code).trim())
  }
}

/**
 * 静默登录：小程序优先微信 code，失败（如未配置 AppID）再走开发登录。
 */
export async function silentLogin() {
  // #ifdef MP-WEIXIN
  try {
    const loginResult = await new Promise((resolve, reject) => {
      uni.login({ provider: 'weixin', success: resolve, fail: reject })
    })
    const body = await post('/api/app/auth/login', { code: loginResult.code })
    if (body.code === 200 && body.data && body.data.token) {
      setToken(body.data.token)
      return body.data
    }
  } catch (e) {
    // 微信登录不可用时降级到开发登录
  }
  // #endif

  const body = await post('/api/app/auth/dev-login', { phone: getDevPhone() })
  if (body.code !== 200 || !body.data || !body.data.token) {
    const err = new Error(body.msg || '登录失败')
    err.code = body.code
    throw err
  }
  setToken(body.data.token)
  return body.data
}

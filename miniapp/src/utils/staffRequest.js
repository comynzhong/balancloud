import { BASE_URL } from './config'
import { getTenantCode } from './auth'

/** 运维会话独立存储：与 C 端 bc_app_token 分开，两边互不覆盖。 */
const TOKEN_KEY = 'bc_staff_token'
const LOGIN_PAGE = 'pkg-staff/pages/login/index'

export class StaffApiError extends Error {
  constructor(message, code) {
    super(message)
    this.code = code
  }
}

export function getStaffToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function setStaffToken(token) {
  uni.setStorageSync(TOKEN_KEY, token)
}

export function clearStaffToken() {
  uni.removeStorageSync(TOKEN_KEY)
}

function send(url, method, data, header) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data: data || {},
      header: { 'Content-Type': 'application/json', ...header },
      timeout: 15000,
      success: (res) => resolve(res.data || {}),
      fail: () => reject(new StaffApiError('网络不可用，请检查现场网络后重试', -1))
    })
  })
}

function goStaffLogin() {
  const pages = typeof getCurrentPages === 'function' ? getCurrentPages() : []
  const current = pages.length ? pages[pages.length - 1].route : ''
  if (current === LOGIN_PAGE) return
  uni.navigateTo({ url: '/' + LOGIN_PAGE })
}

/**
 * 运维请求：只带后台 JWT，401/403 立即清会话并回登录页。
 * silent 用于入口探测——探不到只是不显示入口，不能把用户从 C 端页面拽走。
 */
export default async function staffRequest(options) {
  const header = {}
  const token = getStaffToken()
  if (token) header.Authorization = 'Bearer ' + token
  if (options.tenantCode) {
    const tenantCode = getTenantCode()
    if (tenantCode) header['X-Tenant-Code'] = tenantCode
  }
  const body = await send(options.url, options.method || 'GET', options.data, header)
  const code = body.code
  if (code === 401 || code === 403) {
    clearStaffToken()
    if (!options.silent) goStaffLogin()
    throw new StaffApiError(body.msg || '登录已失效，请重新登录', code)
  }
  if (code !== 200) {
    throw new StaffApiError(body.msg || '请求失败', code)
  }
  return options.raw ? body : body.data
}

export { LOGIN_PAGE as STAFF_LOGIN_PAGE }

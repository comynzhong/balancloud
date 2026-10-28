import { BASE_URL } from './config'
import { getToken, clearToken, silentLogin } from './auth'

export class ApiError extends Error {
  constructor(message, code) {
    super(message)
    this.code = code
  }
}

function send(options) {
  return new Promise((resolve, reject) => {
    const token = getToken()
    const header = { 'Content-Type': 'application/json' }
    if (token) {
      header.Authorization = 'Bearer ' + token
    }
    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header,
      timeout: 15000,
      success: (res) => resolve(res.data || {}),
      fail: () => reject(new ApiError('网络连接失败，请检查网络后重试', -1))
    })
  })
}

/**
 * 统一请求：业务错误按 body.code 判定；401 静默登录后重试一次。
 */
export default async function request(options) {
  const opts = { auth: true, ...options }
  let body
  try {
    body = await send(opts)
  } catch (e) {
    throw e instanceof ApiError ? e : new ApiError('网络连接失败，请检查网络后重试', -1)
  }

  if (body.code === 401) {
    clearToken()
    if (!opts.auth || opts.retried) {
      uni.reLaunch({ url: '/pages/login/login' })
      throw new ApiError('登录已失效，请重新登录', 401)
    }
    try {
      await silentLogin()
    } catch (e) {
      uni.reLaunch({ url: '/pages/login/login' })
      throw new ApiError(e.message || '登录失败', 401)
    }
    return request({ ...opts, retried: true })
  }

  if (body.code !== 200) {
    throw new ApiError(body.msg || '请求失败', body.code)
  }
  return body.data
}

export function showError(e) {
  uni.showToast({ title: (e && e.message) || '操作失败', icon: 'none', duration: 2500 })
}

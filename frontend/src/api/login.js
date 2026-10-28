import request from '@/utils/request'

// 登录方法：自建租户把编码放在 X-Tenant-Code 头里，留空则走默认运营商
export function login(data, tenantCode) {
  const headers = {
    isToken: false,
    repeatSubmit: false
  }
  if (tenantCode) {
    headers['X-Tenant-Code'] = tenantCode
  }
  return request({
    url: '/login',
    headers,
    method: 'post',
    data: data
  })
}

// 注册方法
export function register(data) {
  return request({
    url: '/register',
    headers: {
      isToken: false
    },
    method: 'post',
    data: data
  })
}

// 获取用户详细信息
export function getInfo() {
  return request({
    url: '/getInfo',
    method: 'get'
  })
}

// 退出方法
export function logout() {
  return request({
    url: '/logout',
    method: 'post'
  })
}

// 获取滑块验证码
export function getSliderCaptcha() {
  return request({
    url: '/captcha/slider',
    headers: {
      isToken: false
    },
    method: 'get',
    timeout: 20000
  })
}

// 校验滑块位置
export function verifySliderCaptcha(id, x) {
  return request({
    url: '/captcha/slider/verify',
    headers: {
      isToken: false,
      repeatSubmit: false
    },
    method: 'post',
    data: { id, x }
  })
}

// 获取验证码
export function getCodeImg() {
  return request({
    url: '/captchaImage',
    headers: {
      isToken: false
    },
    method: 'get',
    timeout: 20000
  })
}
import request from '@/utils/request'

export function getPublicConfig() {
  return request({
    url: '/api/v1/public/config',
    method: 'get',
    headers: { isToken: false }
  })
}

// 入驻入口是否开放：社区版与关掉多租户时返回 false，登录页据此隐藏入口
export function getRegistrationStatus() {
  return request({
    url: '/api/v1/public/tenant-registration',
    method: 'get',
    headers: { isToken: false }
  })
}

export function applyTenantRegistration(data) {
  return request({
    url: '/api/v1/public/tenant-registration',
    method: 'post',
    headers: { isToken: false },
    data
  })
}

export function getConfigGroups() {
  return request({
    url: '/api/v1/system/config',
    method: 'get'
  })
}

export function updateConfig(values) {
  return request({
    url: '/api/v1/system/config',
    method: 'put',
    data: { values }
  })
}

export function getConfigValues() {
  return request({
    url: '/api/v1/system/config/values',
    method: 'get'
  })
}

export function createConfigItem(data) {
  return request({
    url: '/api/v1/system/config/items',
    method: 'post',
    data
  })
}

export function updateConfigItem(configKey, data) {
  return request({
    url: `/api/v1/system/config/items/${encodeURIComponent(configKey)}`,
    method: 'put',
    data
  })
}

export function deleteConfigItem(configKey) {
  return request({
    url: `/api/v1/system/config/items/${encodeURIComponent(configKey)}`,
    method: 'delete'
  })
}

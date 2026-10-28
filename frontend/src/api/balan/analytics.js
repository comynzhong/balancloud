import request from '@/utils/request'

export function getDeviceAnalytics(params) {
  return request({
    url: '/api/v1/analytics/devices',
    method: 'get',
    params
  })
}

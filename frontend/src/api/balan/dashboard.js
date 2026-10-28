import request from '@/utils/request'

export function getDashboardSummary(params) {
  return request({
    url: '/api/v1/dashboard/summary',
    method: 'get',
    params
  })
}

export function getPowerTrend(params) {
  return request({
    url: '/api/v1/dashboard/power-trend',
    method: 'get',
    params
  })
}

export function getEnergyTrend(params) {
  return request({
    url: '/api/v1/dashboard/energy-trend',
    method: 'get',
    params
  })
}

export function getBusinessTrend(params) {
  return request({
    url: '/api/v1/dashboard/business-trend',
    method: 'get',
    params
  })
}

export function getStationOverview(params) {
  return request({
    url: '/api/v1/dashboard/stations',
    method: 'get',
    params
  })
}

export function getGroupOverview(params) {
  return request({
    url: '/api/v1/dashboard/groups',
    method: 'get',
    params
  })
}

export function getAlertStats(params) {  return request({
    url: '/api/v1/dashboard/alert-stats',
    method: 'get',
    params
  })
}

export function listDashboardEvents(params) {
  return request({
    url: '/api/v1/dashboard/events',
    method: 'get',
    params
  })
}

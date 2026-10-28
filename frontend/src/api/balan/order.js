import request from '@/utils/request'

export function listOrder(query) {
  return request({
    url: '/api/v1/orders',
    method: 'get',
    params: query
  })
}

export function getOrder(id) {
  return request({
    url: '/api/v1/orders/' + id,
    method: 'get'
  })
}

export function payOrder(id) {
  return request({
    url: '/api/v1/orders/' + id + '/pay',
    method: 'post'
  })
}

export function listActiveOrders(query) {
  return request({
    url: '/api/v1/orders/active',
    method: 'get',
    params: query
  })
}

export function refundOrder(id, data) {
  return request({
    url: '/api/v1/orders/' + id + '/refund',
    method: 'post',
    data
  })
}

export function forceFinishOrder(id, data) {
  return request({
    url: '/api/v1/orders/' + id + '/force-finish',
    method: 'post',
    data
  })
}

export function correctOrderAmount(id, data) {
  return request({
    url: '/api/v1/orders/' + id + '/correct-amount',
    method: 'post',
    data
  })
}

export function listOrderManualLogs(id) {
  return request({
    url: '/api/v1/orders/' + id + '/manual-logs',
    method: 'get'
  })
}

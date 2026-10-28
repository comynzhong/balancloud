import request from '@/utils/request'

export function listAlert(query) {
  return request({
    url: '/api/v1/alerts',
    method: 'get',
    params: query
  })
}

export function getAlert(id) {
  return request({
    url: '/api/v1/alerts/' + id,
    method: 'get'
  })
}

export function ackAlert(id) {
  return request({
    url: '/api/v1/alerts/' + id + '/ack',
    method: 'post'
  })
}

export function resolveAlert(id) {
  return request({
    url: '/api/v1/alerts/' + id + '/resolve',
    method: 'post'
  })
}

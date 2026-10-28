import request from '@/utils/request'

export function listRate(query) {
  return request({
    url: '/api/v1/rate-strategies',
    method: 'get',
    params: query
  })
}

export function getRate(id) {
  return request({
    url: '/api/v1/rate-strategies/' + id,
    method: 'get'
  })
}

export function addRate(data) {
  return request({
    url: '/api/v1/rate-strategies',
    method: 'post',
    data
  })
}

export function updateRate(id, data) {
  return request({
    url: '/api/v1/rate-strategies/' + id,
    method: 'put',
    data
  })
}

export function delRate(id) {
  return request({
    url: '/api/v1/rate-strategies/' + id,
    method: 'delete'
  })
}

export function enableRate(id) {
  return request({
    url: '/api/v1/rate-strategies/' + id + '/enable',
    method: 'post'
  })
}

export function disableRate(id) {
  return request({
    url: '/api/v1/rate-strategies/' + id + '/disable',
    method: 'post'
  })
}

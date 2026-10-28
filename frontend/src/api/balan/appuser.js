import request from '@/utils/request'

export function listAppUser(query) {
  return request({
    url: '/api/v1/app-users',
    method: 'get',
    params: query
  })
}

export function getAppUser(id) {
  return request({
    url: '/api/v1/app-users/' + id,
    method: 'get'
  })
}

export function changeAppUserStatus(id, status) {
  return request({
    url: '/api/v1/app-users/' + id + '/status',
    method: 'put',
    params: { status }
  })
}

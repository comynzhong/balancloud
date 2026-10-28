import request from '@/utils/request'

export function listBlacklist(query) {
  return request({
    url: '/api/v1/app-blacklist',
    method: 'get',
    params: query
  })
}

export function listBlacklistUserOptions(keyword) {
  return request({
    url: '/api/v1/app-blacklist/users',
    method: 'get',
    params: { keyword }
  })
}

export function addBlacklist(data) {
  return request({
    url: '/api/v1/app-blacklist',
    method: 'post',
    data
  })
}

export function updateBlacklist(id, data) {
  return request({
    url: '/api/v1/app-blacklist/' + id,
    method: 'put',
    data
  })
}

export function delBlacklist(id) {
  return request({
    url: '/api/v1/app-blacklist/' + id,
    method: 'delete'
  })
}

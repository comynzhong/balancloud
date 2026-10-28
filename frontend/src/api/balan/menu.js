import request from '@/utils/request'

export function listMenu(query) {
  return request({
    url: '/api/v1/system/menu/list',
    method: 'get',
    params: query
  })
}

export function getMenu(menuId) {
  return request({
    url: '/api/v1/system/menu/' + menuId,
    method: 'get'
  })
}

export function menuOptions(excludeId) {
  return request({
    url: '/api/v1/system/menu/options',
    method: 'get',
    params: { excludeId }
  })
}

export function addMenu(data) {
  return request({
    url: '/api/v1/system/menu',
    method: 'post',
    data
  })
}

export function updateMenu(menuId, data) {
  return request({
    url: '/api/v1/system/menu/' + menuId,
    method: 'put',
    data
  })
}

export function deleteMenu(menuId) {
  return request({
    url: '/api/v1/system/menu/' + menuId,
    method: 'delete'
  })
}

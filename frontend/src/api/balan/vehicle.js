import request from '@/utils/request'

export function listVehicle(query) {
  return request({
    url: '/api/v1/vehicles',
    method: 'get',
    params: query
  })
}

export function getVehicle(id) {
  return request({
    url: '/api/v1/vehicles/' + id,
    method: 'get'
  })
}

export function addVehicle(data) {
  return request({
    url: '/api/v1/vehicles',
    method: 'post',
    data
  })
}

export function updateVehicle(id, data) {
  return request({
    url: '/api/v1/vehicles/' + id,
    method: 'put',
    data
  })
}

export function deleteVehicle(id) {
  return request({
    url: '/api/v1/vehicles/' + id,
    method: 'delete'
  })
}

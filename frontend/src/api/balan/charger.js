import request from '@/utils/request'

export function listCharger(query) {
  return request({
    url: '/api/v1/chargers',
    method: 'get',
    params: query
  })
}

export function getChargerGroups() {
  return request({
    url: '/api/v1/chargers/groups',
    method: 'get'
  })
}

export function getChargerScenes() {
  return request({
    url: '/api/v1/chargers/scenes',
    method: 'get'
  })
}

export function getDeviceProtocols() {
  return request({
    url: '/api/v1/device/protocols',
    method: 'get'
  })
}

export function getCharger(chargerId) {  return request({
    url: '/api/v1/chargers/' + chargerId,
    method: 'get'
  })
}

export function getChargerPorts(chargerId) {
  return request({
    url: '/api/v1/chargers/' + chargerId + '/ports',
    method: 'get'
  })
}

export function getChargerHistory(chargerId, params) {
  return request({
    url: '/api/v1/chargers/' + chargerId + '/history',
    method: 'get',
    params
  })
}

export function addCharger(data) {
  return request({
    url: '/api/v1/chargers',
    method: 'post',
    data
  })
}

export function updateCharger(chargerId, data) {
  return request({
    url: '/api/v1/chargers/' + chargerId,
    method: 'put',
    data
  })
}

export function delCharger(chargerId) {
  return request({
    url: '/api/v1/chargers/' + chargerId,
    method: 'delete'
  })
}

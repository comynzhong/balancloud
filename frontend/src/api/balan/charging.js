import request from '@/utils/request'

export function startCharging(chargerId, portNo, data) {
  return request({
    url: `/api/v1/chargers/${chargerId}/ports/${portNo}/start`,
    method: 'post',
    data
  })
}

export function stopCharging(chargerId, portNo) {
  return request({
    url: `/api/v1/chargers/${chargerId}/ports/${portNo}/stop`,
    method: 'post'
  })
}

export function unlockPort(chargerId, portNo) {
  return request({
    url: `/api/v1/chargers/${chargerId}/ports/${portNo}/unlock`,
    method: 'post'
  })
}

export function setPortThreshold(chargerId, portNo, data) {
  return request({
    url: `/api/v1/chargers/${chargerId}/ports/${portNo}/threshold`,
    method: 'put',
    data
  })
}

export function getCommand(cmdId) {
  return request({
    url: '/api/v1/commands/' + cmdId,
    method: 'get'
  })
}

import request from '@/utils/request'

export function listStation(query) {
  return request({
    url: '/api/v1/stations',
    method: 'get',
    params: query
  })
}

export function getStationOptions() {
  return request({
    url: '/api/v1/stations/options',
    method: 'get'
  })
}

export function getStation(stationId) {
  return request({
    url: '/api/v1/stations/' + stationId,
    method: 'get'
  })
}

export function addStation(data) {
  return request({
    url: '/api/v1/stations',
    method: 'post',
    data
  })
}

export function updateStation(stationId, data) {
  return request({
    url: '/api/v1/stations/' + stationId,
    method: 'put',
    data
  })
}

export function delStation(stationId) {
  return request({
    url: '/api/v1/stations/' + stationId,
    method: 'delete'
  })
}

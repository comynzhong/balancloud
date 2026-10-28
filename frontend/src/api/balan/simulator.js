import request from '@/utils/request'

export function getSimulatorStatus() {
  return request({
    url: '/api/v1/simulator/status',
    method: 'get'
  })
}

export function updateSimulatorConfig(data) {
  return request({
    url: '/api/v1/simulator/config',
    method: 'put',
    data
  })
}

export function startSimulator() {
  return request({
    url: '/api/v1/simulator/start',
    method: 'post'
  })
}

export function stopSimulator() {
  return request({
    url: '/api/v1/simulator/stop',
    method: 'post'
  })
}

export function batchCreateSimulated(data) {
  return request({
    url: '/api/v1/simulator/batch',
    method: 'post',
    data
  })
}

export function injectFault(data) {
  return request({
    url: '/api/v1/simulator/fault',
    method: 'post',
    data
  })
}

export function unplugSimulated(data) {
  return request({
    url: '/api/v1/simulator/unplug',
    method: 'post',
    data
  })
}

export function getOcppSimulatorStatus() {
  return request({
    url: '/api/v1/device/ocpp/simulators',
    method: 'get'
  })
}

export function startOcppSimulator(data) {
  return request({
    url: '/api/v1/device/ocpp/simulators',
    method: 'post',
    data
  })
}

export function stopOcppSimulator(identity) {
  return request({
    url: '/api/v1/device/ocpp/simulators',
    method: 'delete',
    params: { identity }
  })
}

export function listMqttLogs(params) {
  return request({
    url: '/api/v1/mqtt/logs',
    method: 'get',
    params
  })
}

export function clearMqttLogs() {
  return request({
    url: '/api/v1/mqtt/logs',
    method: 'delete'
  })
}

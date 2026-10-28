import request from '@/utils/request'

export function listWorkOrder(query) {
  return request({
    url: '/api/v1/work-orders',
    method: 'get',
    params: query
  })
}

export function getWorkOrder(id) {
  return request({
    url: '/api/v1/work-orders/' + id,
    method: 'get'
  })
}

export function getWorkOrderStats() {
  return request({
    url: '/api/v1/work-orders/stats',
    method: 'get'
  })
}

export function getWorkOrderAssignees() {
  return request({
    url: '/api/v1/work-orders/assignees',
    method: 'get'
  })
}

export function addWorkOrder(data) {
  return request({
    url: '/api/v1/work-orders',
    method: 'post',
    data
  })
}

export function addWorkOrderFromAlert(alertId, data) {
  return request({
    url: '/api/v1/work-orders/from-alert/' + alertId,
    method: 'post',
    data: data || {}
  })
}

export function updateWorkOrder(id, data) {
  return request({
    url: '/api/v1/work-orders/' + id,
    method: 'put',
    data
  })
}

export function assignWorkOrder(id, assigneeId) {
  return request({
    url: '/api/v1/work-orders/' + id + '/assign',
    method: 'put',
    data: { assigneeId }
  })
}

export function addWorkOrderRecord(id, content) {
  return request({
    url: '/api/v1/work-orders/' + id + '/records',
    method: 'post',
    data: { content }
  })
}

export function resolveWorkOrder(id, resolution) {
  return request({
    url: '/api/v1/work-orders/' + id + '/resolve',
    method: 'post',
    data: { resolution }
  })
}

export function reopenWorkOrder(id, remark) {
  return request({
    url: '/api/v1/work-orders/' + id + '/reopen',
    method: 'post',
    data: { remark }
  })
}

export function closeWorkOrder(id, remark) {
  return request({
    url: '/api/v1/work-orders/' + id + '/close',
    method: 'post',
    data: { remark }
  })
}

export function delWorkOrder(id) {
  return request({
    url: '/api/v1/work-orders/' + id,
    method: 'delete'
  })
}

import request from '@/utils/request'

export function listUser(query) {
  return request({
    url: '/api/v1/system/users',
    method: 'get',
    params: query
  })
}

export function getUser(userId) {
  return request({
    url: '/api/v1/system/users/' + userId,
    method: 'get'
  })
}

export function addUser(data) {
  return request({
    url: '/api/v1/system/users',
    method: 'post',
    data
  })
}

export function updateUser(userId, data) {
  return request({
    url: '/api/v1/system/users/' + userId,
    method: 'put',
    data
  })
}

export function changeUserStatus(userId, status) {
  return request({
    url: '/api/v1/system/users/' + userId + '/status',
    method: 'put',
    params: { status }
  })
}

export function resetUserPassword(userId, password) {
  return request({
    url: '/api/v1/system/users/' + userId + '/reset-password',
    method: 'post',
    data: { password }
  })
}

export function delUser(userId) {
  return request({
    url: '/api/v1/system/users/' + userId,
    method: 'delete'
  })
}

export function listRole() {
  return request({
    url: '/api/v1/system/roles',
    method: 'get'
  })
}

export function getRoleMenus(roleId) {
  return request({
    url: '/api/v1/system/roles/' + roleId + '/menus',
    method: 'get'
  })
}

export function grantableMenuTree() {
  return request({
    url: '/api/v1/system/roles/menu-tree',
    method: 'get'
  })
}

export function addRole(data) {
  return request({
    url: '/api/v1/system/roles',
    method: 'post',
    data
  })
}

export function updateRole(roleId, data) {
  return request({
    url: '/api/v1/system/roles/' + roleId,
    method: 'put',
    data
  })
}

export function changeRoleStatus(roleId, status) {
  return request({
    url: '/api/v1/system/roles/' + roleId + '/status',
    method: 'put',
    params: { status }
  })
}

export function grantRoleMenus(roleId, menuIds) {
  return request({
    url: '/api/v1/system/roles/' + roleId + '/menus',
    method: 'put',
    data: menuIds
  })
}

export function delRole(roleId) {
  return request({
    url: '/api/v1/system/roles/' + roleId,
    method: 'delete'
  })
}

export function listOperLog(query) {
  return request({
    url: '/api/v1/system/logs/oper',
    method: 'get',
    params: query
  })
}

export function getOperLog(logId) {
  return request({
    url: '/api/v1/system/logs/oper/' + logId,
    method: 'get'
  })
}

export function listOperLogTitles() {
  return request({
    url: '/api/v1/system/logs/oper/titles',
    method: 'get'
  })
}

export function listLoginLog(query) {
  return request({
    url: '/api/v1/system/logs/login',
    method: 'get',
    params: query
  })
}

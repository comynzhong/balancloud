import request from '@/utils/request'

export function listMessages(query) {
  return request({
    url: '/api/v1/system/messages',
    method: 'get',
    params: query
  })
}

export function getUnreadMessageCount() {
  return request({
    url: '/api/v1/system/messages/unread-count',
    method: 'get'
  })
}

export function markMessageRead(id) {
  return request({
    url: '/api/v1/system/messages/' + id + '/read',
    method: 'post'
  })
}

export function markAllMessagesRead() {
  return request({
    url: '/api/v1/system/messages/read-all',
    method: 'post'
  })
}

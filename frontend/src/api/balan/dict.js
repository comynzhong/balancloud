import request from '@/utils/request'

export function listDictType(query) {
  return request({
    url: '/api/v1/system/dict/types',
    method: 'get',
    params: query
  })
}

export function addDictType(data) {
  return request({
    url: '/api/v1/system/dict/types',
    method: 'post',
    data
  })
}

export function updateDictType(id, data) {
  return request({
    url: '/api/v1/system/dict/types/' + id,
    method: 'put',
    data
  })
}

export function deleteDictType(id) {
  return request({
    url: '/api/v1/system/dict/types/' + id,
    method: 'delete'
  })
}

export function listDictData(query) {
  return request({
    url: '/api/v1/system/dict/data',
    method: 'get',
    params: query
  })
}

export function addDictData(data) {
  return request({
    url: '/api/v1/system/dict/data',
    method: 'post',
    data
  })
}

export function updateDictData(id, data) {
  return request({
    url: '/api/v1/system/dict/data/' + id,
    method: 'put',
    data
  })
}

export function deleteDictData(id) {
  return request({
    url: '/api/v1/system/dict/data/' + id,
    method: 'delete'
  })
}

export function getDictOptions(dictType) {
  return request({
    url: '/api/v1/system/dict/options/' + dictType,
    method: 'get'
  })
}

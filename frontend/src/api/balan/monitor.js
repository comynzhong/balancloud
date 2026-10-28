import request from '@/utils/request'

// 社区版「系统监控 / 节点状态」：四大中间件探活 + 当前 JVM
export function getNodeStatus() {
  return request({
    url: '/api/v1/monitor/node',
    method: 'get'
  })
}

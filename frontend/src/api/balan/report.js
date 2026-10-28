import request from '@/utils/request'

// 运营晨报：date 为空时后端按「昨天」处理
export function getMorningReport(date) {
  return request({
    url: '/api/v1/report/morning',
    method: 'get',
    params: date ? { date } : {}
  })
}

// 手动推送是租户级动作，仅超管可用
export function pushMorningReport(date) {
  return request({
    url: '/api/v1/report/morning/push',
    method: 'post',
    params: date ? { date } : {}
  })
}

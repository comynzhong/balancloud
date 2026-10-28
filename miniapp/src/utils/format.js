export function money(value) {
  const n = Number(value || 0)
  return n.toFixed(2)
}

export function kwh(value) {
  const n = Number(value || 0)
  return n.toFixed(4)
}

export function duration(seconds) {
  const total = Math.max(0, Number(seconds || 0))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = Math.floor(total % 60)
  if (h > 0) return `${h}小时${m}分`
  if (m > 0) return `${m}分${s}秒`
  return `${s}秒`
}

export function countdown(seconds) {
  const total = Math.max(0, Number(seconds || 0))
  const m = Math.floor(total / 60)
  const s = Math.floor(total % 60)
  return `${m}:${String(s).padStart(2, '0')}`
}

export function datetime(value) {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(0, 19)
}

export function shortTime(value) {
  if (!value) return '--'
  const text = String(value).replace('T', ' ')
  return text.slice(5, 16)
}

export function maskedPhone(phone) {
  if (!phone) return '未绑定手机号'
  return String(phone).replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
}

export const ORDER_STATUS = {
  PENDING_START: '启动中',
  CHARGING: '充电中',
  STOPPING: '结算中',
  COMPLETED: '已完成',
  ABNORMAL: '异常结束',
  FAILED: '启动失败'
}

export const ACTIVE_STATUSES = ['PENDING_START', 'CHARGING', 'STOPPING']

export function orderStatus(status) {
  return ORDER_STATUS[status] || status || '--'
}

export function isActive(status) {
  return ACTIVE_STATUSES.includes(status)
}

export const STOP_REASON = {
  USER_STOP: '用户停止',
  DEVICE_STOP: '设备停止',
  FULL: '充电完成',
  TIME_LIMIT: '达到时长上限',
  AMOUNT_LIMIT: '达到金额上限',
  NO_LOAD: '未检测到负载',
  FAULT: '设备故障',
  DEVICE_OFFLINE: '设备离线',
  STOP_TIMEOUT: '停止超时',
  START_FAILED: '启动失败'
}

export function stopReason(code, message) {
  const label = STOP_REASON[code]
  if (label) return label
  return message || code || '--'
}

export const PERIOD_LABEL = {
  PEAK: '峰',
  FLAT: '平',
  VALLEY: '谷'
}

export function periodLabel(type) {
  return PERIOD_LABEL[type] || type
}

export function paymentStatus(status) {
  if (status === 'PAID') return '已支付'
  if (status === 'REFUNDED') return '已退款'
  return '未支付'
}

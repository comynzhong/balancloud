import { parseTime } from '@/utils/ruoyi'

export const deviceTypeOptions = [
  { value: 'SOCKET_2', label: '2 路插座' },
  { value: 'SOCKET_10', label: '10 路插座' },
  { value: 'SOCKET_12', label: '12 路插座' },
  { value: 'DC_SINGLE', label: '直流单枪' },
  { value: 'DC_DUAL', label: '直流双枪' },
  { value: 'AC_SINGLE', label: '交流单枪' },
  { value: 'AC_DUAL', label: '交流双枪' }
]

export const startMethodOptions = [
  { value: 'SCAN', label: '扫码启动' },
  { value: 'PLUG_AND_CHARGE', label: '插枪即充' }
]

export function startMethodLabel(method) {
  return startMethodOptions.find(item => item.value === method)?.label || method || '-'
}

export const billingModeOptions = [
  { value: 'TIME', label: '按时长' },
  { value: 'ENERGY', label: '按电量' },
  { value: 'MIXED', label: '混合计费' }
]

export const strategyTypeOptions = [
  { value: 'ENERGY', label: '电价策略' },
  { value: 'SERVICE', label: '服务费策略' }
]

export const periodTypeOptions = [
  { value: 'PEAK', label: '峰' },
  { value: 'FLAT', label: '平' },
  { value: 'VALLEY', label: '谷' }
]

export const orderStatusOptions = [
  { value: 'PENDING_START', label: '启动中' },
  { value: 'CHARGING', label: '充电中' },
  { value: 'STOPPING', label: '停止中' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'FAILED', label: '启动失败' },
  { value: 'ABNORMAL', label: '异常结束' }
]

export const paymentStatusOptions = [
  { value: 'UNPAID', label: '未支付' },
  { value: 'PAID', label: '已支付' },
  { value: 'REFUNDED', label: '已退款' }
]

export const alertTypeOptions = [
  { value: 'OVER_TEMP', label: '过温' },
  { value: 'OVER_CURRENT', label: '过流' },
  { value: 'OVER_VOLTAGE', label: '过压' },
  { value: 'UNDER_VOLTAGE', label: '欠压' },
  { value: 'OVERLOAD', label: '过载' },
  { value: 'OFFLINE', label: '设备离线' },
  { value: 'PORT_FAULT', label: '端口故障' },
  { value: 'STATE_MISMATCH', label: '状态不一致' }
]

export const alertLevelOptions = [
  { value: 'CRITICAL', label: '紧急' },
  { value: 'MAJOR', label: '重要' },
  { value: 'INFO', label: '提示' }
]

export const alertStatusOptions = [
  { value: 'ACTIVE', label: '处理中' },
  { value: 'OPEN', label: '未处理' },
  { value: 'ACKNOWLEDGED', label: '已确认' },
  { value: 'RESOLVED', label: '已恢复' }
]

export const alertSourceOptions = [
  { value: 'PLATFORM', label: '平台判定' },
  { value: 'DEVICE', label: '设备上报' }
]

export function deviceTypeLabel(type) {
  return deviceTypeOptions.find(item => item.value === type)?.label || type || '-'
}

// 协议名同样来自后端注册表，这里只翻中文；未登记的编码原样显示
const protocolLabels = {
  BALAN_MQTT: '内置 MQTT',
  OCPP16J: 'OCPP 1.6J'
}

export function protocolLabel(protocol) {
  return protocolLabels[protocol] || protocol || '-'
}

// 场景由后端注册表决定，这里只负责把已知编码翻成中文；未登记的编码原样显示
const sceneLabels = {
  TWOWHEEL: '两轮插座',
  AC_CAR: '交流汽车',
  DC_CAR: '直流快充',
  HEAVY: '大型车'
}

const preChargePhaseLabels = {
  PLUGGED: '插枪',
  AUTHENTICATING: '鉴权',
  HANDSHAKING: '握手',
  BMS_NEGOTIATING: 'BMS 协商',
  PRE_CHARGE: '预充'
}

export function sceneLabel(scene) {
  return sceneLabels[scene] || scene || '-'
}

export function preChargePhaseLabel(phase) {
  return preChargePhaseLabels[phase] || phase || '-'
}

export function billingModeLabel(mode) {
  return billingModeOptions.find(item => item.value === mode)?.label || mode || '-'
}

export function strategyTypeLabel(type) {
  return strategyTypeOptions.find(item => item.value === type)?.label || type || '电价策略'
}

export function periodTypeLabel(type) {
  return periodTypeOptions.find(item => item.value === type)?.label || type || '-'
}

export function orderStatusLabel(status) {
  return orderStatusOptions.find(item => item.value === status)?.label || status || '-'
}

export function orderStatusType(status) {
  return {
    PENDING_START: 'warning',
    CHARGING: 'success',
    STOPPING: 'warning',
    COMPLETED: 'success',
    FAILED: 'danger',
    ABNORMAL: 'danger'
  }[status] || 'info'
}

export function paymentStatusLabel(status) {
  return paymentStatusOptions.find(item => item.value === status)?.label || status || '-'
}

export function paymentStatusType(status) {
  return {
    UNPAID: 'info',
    PAID: 'success',
    REFUNDED: 'warning'
  }[status] || 'info'
}

export function stopReasonLabel(reason) {
  return {
    USER_STOP: '用户停止',
    DEVICE_STOP: '设备停止',
    FULL: '充满自停',
    TIME_LIMIT: '达到时长上限',
    AMOUNT_LIMIT: '达到金额上限',
    NO_LOAD: '空载自停',
    FAULT: '设备故障',
    DEVICE_OFFLINE: '设备离线',
    STOP_TIMEOUT: '停止超时',
    MANUAL_FORCE: '人工强制结束',
    START_FAILED: '启动失败'
  }[reason] || reason || '-'
}

export function formatTime(value) {
  if (typeof value === 'string') {
    value = value.replace(/\.\d+/, '')
  }
  return parseTime(value) || '-'
}

export function formatNumber(value, digits = 2) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return Number(value).toFixed(digits)
}

export function formatMoney(value) {
  return '¥' + formatNumber(value, 2)
}

export function formatDuration(seconds) {
  const total = Number(seconds || 0)
  if (total <= 0) {
    return '0 分钟'
  }
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (hours > 0) {
    return `${hours} 小时 ${minutes} 分`
  }
  return `${Math.max(minutes, 1)} 分钟`
}

export function portStatusLabel(status) {
  return { IDLE: '空闲', CHARGING: '充电中', OCCUPIED: '占用中', FAULT: '故障' }[status] || status || '-'
}

export function portStatusType(status) {
  return { IDLE: 'info', CHARGING: 'success', OCCUPIED: 'warning', FAULT: 'danger' }[status] || 'info'
}

export function alertTypeLabel(type) {
  return alertTypeOptions.find(item => item.value === type)?.label || type || '-'
}

export function alertLevelLabel(level) {
  return alertLevelOptions.find(item => item.value === level)?.label || level || '-'
}

export function alertLevelType(level) {
  return { CRITICAL: 'danger', MAJOR: 'warning', INFO: 'info' }[level] || 'info'
}

export function alertStatusLabel(status) {
  return alertStatusOptions.find(item => item.value === status)?.label || status || '-'
}

export function alertStatusType(status) {
  return {
    OPEN: 'danger',
    ACKNOWLEDGED: 'warning',
    RESOLVED: 'success'
  }[status] || 'info'
}

export function alertSourceLabel(source) {
  return alertSourceOptions.find(item => item.value === source)?.label || source || '-'
}

export const workOrderStatusOptions = [
  { value: 'OPEN', label: '待处理' },
  { value: 'PROCESSING', label: '处理中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'CLOSED', label: '已归档' }
]

export const workOrderPriorityOptions = [
  { value: 'LOW', label: '低' },
  { value: 'NORMAL', label: '普通' },
  { value: 'HIGH', label: '高' },
  { value: 'URGENT', label: '紧急' }
]

export const workOrderSourceOptions = [
  { value: 'ALERT', label: '告警生成' },
  { value: 'MANUAL', label: '手动创建' }
]

export function workOrderStatusLabel(status) {
  return workOrderStatusOptions.find(item => item.value === status)?.label || status || '-'
}

export function workOrderStatusType(status) {
  return { OPEN: 'warning', PROCESSING: 'primary', RESOLVED: 'success', CLOSED: 'info' }[status] || 'info'
}

export function workOrderPriorityLabel(priority) {
  return workOrderPriorityOptions.find(item => item.value === priority)?.label || priority || '-'
}

export function workOrderPriorityType(priority) {
  return { LOW: 'info', NORMAL: 'primary', HIGH: 'warning', URGENT: 'danger' }[priority] || 'info'
}

export function workOrderSourceLabel(source) {
  return workOrderSourceOptions.find(item => item.value === source)?.label || source || '-'
}

export function workOrderActionLabel(action) {
  return {
    CREATE: '创建',
    ASSIGN: '指派',
    RECORD: '处理记录',
    RESOLVE: '解决',
    REOPEN: '重新打开',
    CLOSE: '归档',
    EDIT: '编辑'
  }[action] || action || '-'
}

export const businessTypeOptions = [
  { value: 0, label: '其他' },
  { value: 1, label: '新增' },
  { value: 2, label: '修改' },
  { value: 3, label: '删除' },
  { value: 4, label: '授权' },
  { value: 5, label: '导出' },
  { value: 6, label: '导入' },
  { value: 7, label: '强制退出' },
  { value: 8, label: '清空' }
]

export const userStatusOptions = [
  { value: '0', label: '正常' },
  { value: '1', label: '停用' }
]

export function businessTypeLabel(type) {
  return businessTypeOptions.find(item => item.value === type)?.label ?? (type ?? '-')
}

export function userStatusLabel(status) {
  return userStatusOptions.find(item => item.value === String(status))?.label || status || '-'
}

export function loginStatusLabel(status) {
  return String(status) === '0' ? '成功' : '失败'
}

export function loginStatusType(status) {
  return String(status) === '0' ? 'success' : 'danger'
}

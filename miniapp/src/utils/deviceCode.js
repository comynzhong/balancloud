/** 桩体二维码三种格式：BC:CHARGER:编号[:端口]、编号:端口、纯编号。C 端与运维端共用，避免两套解析结果不一致。 */
export function parseScanResult(raw) {
  const text = String(raw || '').trim()
  if (!text) return null
  const match = text.match(/^BC:CHARGER:([^:\s]+)(?::(\d+))?$/)
  if (match) {
    return { deviceNo: match[1], portNo: match[2] || '' }
  }
  const pair = text.match(/^([A-Za-z0-9_-]+):(\d+)$/)
  if (pair) {
    return { deviceNo: pair[1], portNo: pair[2] }
  }
  if (/^[A-Za-z0-9_-]+$/.test(text)) {
    return { deviceNo: text, portNo: '' }
  }
  return null
}

/** 扫码取设备编号；调用方负责把取消与不支持扫码分开处理。 */
export function scanDeviceCode() {
  return new Promise((resolve, reject) => {
    uni.scanCode({
      scanType: ['qrCode', 'barCode'],
      success: (res) => {
        const parsed = parseScanResult(res.result)
        if (parsed) resolve(parsed)
        else reject(new Error('无法识别该二维码内容'))
      },
      fail: reject
    })
  })
}

export function isScanCancel(e) {
  const msg = (e && (e.errMsg || e.message)) || ''
  return msg.indexOf('cancel') >= 0
}

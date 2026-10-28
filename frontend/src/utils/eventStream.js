import { getToken } from '@/utils/auth'

const RECONNECT_DELAY = 5000
const HEARTBEAT_INTERVAL = 25000

const handlers = new Map()
const stateListeners = new Set()

let socket = null
let subscriberCount = 0
let reconnectTimer = null
let heartbeatTimer = null
let manualClose = false

function streamUrl() {
  const base = import.meta.env.VITE_APP_BASE_API || ''
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const token = encodeURIComponent(getToken() || '')
  return `${protocol}//${window.location.host}${base}/ws/events?token=${token}`
}

function emit(type, data) {
  const listeners = handlers.get(type)
  if (!listeners) {
    return
  }
  listeners.forEach(handler => {
    try {
      handler(data)
    } catch (error) {
      console.error('[eventStream] handler error', type, error)
    }
  })
}

function notifyState() {
  const open = !!socket && socket.readyState === WebSocket.OPEN
  stateListeners.forEach(handler => handler(open))
}

function stopTimers() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

function connect() {
  if (socket || subscriberCount === 0) {
    return
  }
  manualClose = false
  const instance = new WebSocket(streamUrl())
  socket = instance
  instance.onopen = () => {
    heartbeatTimer = setInterval(() => {
      if (instance.readyState === WebSocket.OPEN) {
        instance.send('ping')
      }
    }, HEARTBEAT_INTERVAL)
    notifyState()
  }
  instance.onmessage = (event) => {
    let message
    try {
      message = JSON.parse(event.data)
    } catch {
      return
    }
    emit(message.type, message.data)
  }
  instance.onclose = () => {
    if (socket === instance) {
      socket = null
      notifyState()
    }
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
    if (!manualClose && subscriberCount > 0) {
      reconnectTimer = setTimeout(connect, RECONNECT_DELAY)
    }
  }
  instance.onerror = () => instance.close()
}

function disconnectIfIdle() {
  if (subscriberCount > 0) {
    return
  }
  manualClose = true
  stopTimers()
  socket?.close()
  socket = null
  notifyState()
}

/**
 * 订阅后端 /ws/events 事件流，返回取消订阅函数。
 * handlers 形如 { alert: fn, power: fn, order: fn }，onStateChange 接收连接是否在线。
 */
export function subscribeEventStream(handlersMap, onStateChange) {
  const entries = Object.entries(handlersMap || {})
  entries.forEach(([type, handler]) => {
    if (!handlers.has(type)) {
      handlers.set(type, new Set())
    }
    handlers.get(type).add(handler)
    subscriberCount += 1
  })
  if (onStateChange) {
    stateListeners.add(onStateChange)
  }
  connect()
  if (onStateChange) {
    onStateChange(!!socket && socket.readyState === WebSocket.OPEN)
  }
  return () => {
    entries.forEach(([type, handler]) => {
      handlers.get(type)?.delete(handler)
      subscriberCount -= 1
    })
    stateListeners.delete(onStateChange)
    disconnectIfIdle()
  }
}

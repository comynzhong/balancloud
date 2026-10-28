<template>
  <view class="bc-page staff-login">
    <view class="head">
      <text class="head-title">运维工作台</text>
      <text class="head-sub">用后台账号登录，权限、场站范围与手机号脱敏都和电脑端一致</text>
    </view>

    <view class="card">
      <view class="field">
        <text class="label">账号</text>
        <input
          v-model="username"
          class="input"
          placeholder="后台登录账号"
          placeholder-class="ph"
          :maxlength="32"
        />
      </view>
      <view class="field">
        <text class="label">密码</text>
        <input
          v-model="password"
          class="input"
          password
          placeholder="登录密码"
          placeholder-class="ph"
          :maxlength="64"
          @confirm="onPasswordConfirm"
        />
      </view>

      <view v-if="captchaOn" class="captcha">
        <view
          class="board"
          :style="{ height: boardHeight + 'px' }"
          @touchmove.stop.prevent="onBoardDrag"
          @touchend="onHandleRelease"
          @touchcancel="onHandleRelease"
        >
          <image
            v-if="challenge.backgroundImage"
            class="board-bg"
            :src="challenge.backgroundImage"
            mode="scaleToFill"
            :style="{ width: boardWidth + 'px', height: boardHeight + 'px' }"
          />
          <image
            v-if="challenge.pieceImage"
            class="board-piece"
            :src="challenge.pieceImage"
            mode="scaleToFill"
            :style="pieceStyle"
          />
          <text v-if="!challenge.backgroundImage" class="board-hint">正在加载验证码…</text>
          <text v-else-if="verified" class="board-badge">验证通过</text>
        </view>
        <view class="track" :style="{ height: knobSize + 'px' }" @touchmove.stop.prevent="onTrackDrag">
          <text class="track-text">{{ trackText }}</text>
          <view
            class="knob"
            :style="{ left: knobLeft + 'px', width: knobSize + 'px', height: knobSize + 'px' }"
            @touchstart="onHandleStart"
            @touchend="onHandleRelease"
            @touchcancel="onHandleRelease"
          >››</view>
        </view>
        <view class="captcha-foot">
          <text class="captcha-msg">{{ captchaMessage }}</text>
          <text class="link" @tap="loadChallenge">刷新</text>
        </view>
      </view>

      <button class="btn-primary submit" :disabled="busy || !username || !password" :loading="busy" @tap="submit">
        {{ busy ? '正在登录…' : '登录' }}
      </button>
      <view class="hint">拖到缺口对准即自动校验；失败可继续重拖，3 次后需刷新验证码。</view>
      <view v-if="passwordHint" class="warn">{{ passwordHint }}</view>
    </view>

    <view class="card exit" @tap="backToUser">返回用户端</view>
  </view>
</template>

<script setup>
import { computed, getCurrentInstance, ref } from 'vue'
import { onReady } from '@dcloudio/uni-app'
import staffRequest, { setStaffToken } from '@/utils/staffRequest'

const instance = getCurrentInstance()

const CANVAS_WIDTH_FALLBACK = 320
const CANVAS_HEIGHT_FALLBACK = 140
const PIECE_SIZE = 48
const PIECE_IMAGE_WIDTH = 57
const HANDLE_SLACK = 9
const KNOB_SIZE = 44

const username = ref('')
const password = ref('')
const busy = ref(false)
const passwordHint = ref('')

const captchaOn = ref(false)
const challenge = ref({})
const challengeId = ref('')
const captchaToken = ref('')
const verified = ref(false)
const checking = ref(false)
const captchaMessage = ref('')

const boardWidth = ref(0)
const offset = ref(0)
const dragOrigin = ref('')
const dragStartPageX = ref(0)
const dragBaseOffset = ref(0)

const canvasWidth = computed(() => challenge.value.canvasWidth || CANVAS_WIDTH_FALLBACK)
const canvasHeight = computed(() => challenge.value.canvasHeight || CANVAS_HEIGHT_FALLBACK)
const maxOffset = computed(() => challenge.value.maxOffset || (canvasWidth.value - PIECE_SIZE - HANDLE_SLACK))
const scale = computed(() => (boardWidth.value ? boardWidth.value / canvasWidth.value : 1))
const boardHeight = computed(() => Math.round(boardWidth.value * (canvasHeight.value / canvasWidth.value)))
const knobSize = KNOB_SIZE
/** offset 用 320×140 的逻辑坐标（和电脑端 SliderCaptcha 同一口径），渲染时才乘 scale。 */
const knobLeft = computed(() =>
  Math.round(Math.min(offset.value * scale.value, Math.max(0, boardWidth.value - KNOB_SIZE))))
const pieceStyle = computed(() => ({
  left: Math.round(offset.value * scale.value) + 'px',
  top: Math.round((challenge.value.pieceY || 0) * scale.value) + 'px',
  width: Math.round((challenge.value.pieceImageWidth || PIECE_IMAGE_WIDTH) * scale.value) + 'px',
  height: Math.round((challenge.value.pieceSize || PIECE_SIZE) * scale.value) + 'px'
}))
const trackText = computed(() => {
  if (verified.value) return '已完成安全验证'
  if (checking.value) return '正在校验…'
  return '拖动滑块完成拼图'
})

onReady(() => {
  measureBoard()
  loadChallenge()
})

/** 滑块轨道宽度随屏幕变化，量一次真实宽度才能把拖动像素换算回 320 逻辑坐标。
 *  量不到时 scale 退化成 1，此时逻辑坐标与屏幕像素几乎等宽（320 对约 322），误差在容差内。 */
function measureBoard() {
  uni.createSelectorQuery()
    .in(instance.proxy)
    .select('.track')
    .boundingClientRect((rect) => {
      if (rect && rect.width) boardWidth.value = rect.width
    })
    .exec()
}

async function loadChallenge() {
  verified.value = false
  captchaToken.value = ''
  dragOrigin.value = ''
  offset.value = 0
  captchaMessage.value = ''
  checking.value = false
  try {
    const data = await staffRequest({ url: '/captcha/slider', silent: true })
    if (!data || data.enabled === false) {
      captchaOn.value = false
      return
    }
    captchaOn.value = true
    challengeId.value = data.id
    challenge.value = data
    if (!boardWidth.value) measureBoard()
  } catch (e) {
    captchaOn.value = false
    captchaMessage.value = e.message || '验证码加载失败'
  }
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(value, max))
}

function touchX(event) {
  const touch = (event.touches || [])[0] || (event.changedTouches || [])[0]
  return touch ? touch.pageX : null
}

function beginDrag(event, origin) {
  if (verified.value || checking.value || !challenge.value.backgroundImage) return
  const x = touchX(event)
  if (x === null) return
  dragOrigin.value = origin
  dragStartPageX.value = x
  dragBaseOffset.value = offset.value
}

function moveDrag(event) {
  if (!dragOrigin.value) return
  const x = touchX(event)
  if (x === null) return
  offset.value = clamp(dragBaseOffset.value + (x - dragStartPageX.value) / scale.value, 0, maxOffset.value)
}

/** 拼图块在小程序里容易看得到摸不着，轨道整条都接受拖动，落点按逻辑坐标换算。 */
function onBoardDrag(event) {
  if (!dragOrigin.value) beginDrag(event, 'piece')
  moveDrag(event)
}

function onTrackDrag(event) {
  if (!dragOrigin.value) beginDrag(event, 'handle')
  moveDrag(event)
}

function onHandleStart(event) {
  beginDrag(event, 'handle')
}

function onHandleRelease() {
  if (!dragOrigin.value) return
  dragOrigin.value = ''
  if (verified.value || checking.value || !challenge.value.backgroundImage) return
  verifySlider()
}

async function verifySlider() {
  checking.value = true
  captchaMessage.value = ''
  try {
    const body = await staffRequest({
      url: '/captcha/slider/verify',
      method: 'POST',
      data: { id: challengeId.value, x: Math.round(offset.value) },
      raw: true,
      silent: true
    })
    if (body.verified) {
      verified.value = true
      captchaToken.value = body.token || ''
      await login()
      return
    }
    captchaMessage.value = body.msg
      ? `${body.msg}${body.remaining ? `（剩 ${body.remaining} 次）` : ''}`
      : '验证失败，请重试'
    if (body.expired) {
      setTimeout(loadChallenge, 600)
      return
    }
    offset.value = 0
  } catch (e) {
    captchaMessage.value = e.message || '验证服务暂不可用，请点击刷新'
    offset.value = 0
  } finally {
    checking.value = false
  }
}

async function login() {
  busy.value = true
  passwordHint.value = ''
  try {
    const body = await staffRequest({
      url: '/login',
      method: 'POST',
      data: { username: username.value.trim(), password: password.value, captchaToken: captchaToken.value },
      raw: true,
      tenantCode: true,
      silent: true
    })
    const token = body.token
    if (!token) throw new Error(body.msg || '登录失败')
    setStaffToken(token)
    await enterWorkbench()
  } catch (e) {
    passwordHint.value = e.message || '登录失败'
    if (captchaOn.value) loadChallenge()
  } finally {
    busy.value = false
  }
}

/** 登录成功不等于能用：初始密码账号只能访问改密接口，这里用 profile 判定后再决定去哪。 */
async function enterWorkbench() {
  try {
    await staffRequest({ url: '/api/v1/staff/profile', silent: true })
    uni.redirectTo({ url: '/pkg-staff/pages/workbench/index' })
  } catch (e) {
    if (e.code === 428) {
      passwordHint.value = '该账号需先在电脑端完成首次改密'
      return
    }
    passwordHint.value = e.code === 403 ? '该账号没有运维权限，请联系管理员分配角色' : e.message || '无法进入工作台'
  }
}

function onPasswordConfirm() {
  if (captchaOn.value) return
  login()
}

function submit() {
  if (captchaOn.value && !verified.value) {
    captchaMessage.value = '请先拖动滑块完成安全验证'
    return
  }
  login()
}

function backToUser() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.switchTab({ url: '/pages/mine/index' })
}
</script>

<style lang="scss" scoped>
.head {
  padding: 20rpx 8rpx 32rpx;
  display: flex;
  flex-direction: column;
}

.head-title {
  font-size: 44rpx;
  font-weight: 700;
  color: var(--ink);
}

.head-sub {
  margin-top: 12rpx;
  font-size: 22rpx;
  line-height: 1.6;
  color: var(--ink-3);
}

.field {
  padding: 20rpx 0;
  border-bottom: 2rpx solid var(--line-soft);
}

.label {
  font-size: 22rpx;
  color: var(--ink-3);
}

.input {
  margin-top: 10rpx;
  font-size: 30rpx;
  height: 60rpx;
  line-height: 60rpx;
}

.ph {
  color: #c6cdd5;
  font-size: 26rpx;
}

.captcha {
  margin-top: 28rpx;
}

.board {
  width: 100%;
  position: relative;
  border-radius: 20rpx;
  background: #eef2ef;
  overflow: hidden;
}

.board-bg {
  display: block;
}

.board-piece {
  position: absolute;
  display: block;
}

.board-hint {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  text-align: center;
  font-size: 22rpx;
  color: var(--ink-3);
}

.board-badge {
  position: absolute;
  right: 16rpx;
  top: 16rpx;
  padding: 6rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(0, 168, 112, 0.9);
  color: #ffffff;
  font-size: 20rpx;
}

.track {
  margin-top: 20rpx;
  position: relative;
  border-radius: 999rpx;
  background: #ebefec;
}

.track-text {
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  text-align: center;
  font-size: 22rpx;
  color: var(--ink-2);
}

.knob {
  position: absolute;
  left: 0;
  top: 0;
  border-radius: 50%;
  background: #ffffff;
  color: var(--brand-600);
  font-size: 26rpx;
  font-weight: 700;
  text-align: center;
  line-height: 44px;
  box-shadow: 0 4rpx 12rpx rgba(16, 24, 40, 0.16);
}

.captcha-foot {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 22rpx;
}

.captcha-msg {
  color: var(--red);
  flex: 1;
}

.submit {
  margin-top: 36rpx;
}

.hint {
  margin-top: 20rpx;
  font-size: 20rpx;
  line-height: 1.6;
  color: var(--ink-3);
}

.warn {
  margin-top: 18rpx;
  padding: 18rpx 22rpx;
  border-radius: 18rpx;
  background: var(--red-soft);
  color: #b42318;
  font-size: 22rpx;
  line-height: 1.6;
}

.exit {
  margin-top: 20rpx;
  text-align: center;
  font-size: 26rpx;
  color: var(--ink-2);
  padding: 26rpx;
}
</style>

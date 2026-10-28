<template>
  <view class="bc-page">
    <view class="card scan-card">
      <view class="scan-frame">
        <view class="corner tl"></view>
        <view class="corner tr"></view>
        <view class="corner bl"></view>
        <view class="corner br"></view>
        <view class="scan-beam"></view>
        <text class="frame-hint">对准设备二维码</text>
      </view>
      <view class="scan-tip">支持桩上二维码 / 设备编号标签</view>
      <button class="btn-primary scan-btn" @tap="startScan">开始扫码</button>
    </view>

    <view class="section-title"><text>手动输入</text></view>
    <view class="card form-card">
      <view class="field">
        <text class="label">设备编号</text>
        <input
          v-model="deviceNo"
          class="input"
          placeholder="例如 BC-S12-001"
          placeholder-class="ph"
          @confirm="goConfirm"
        />
      </view>
      <view class="field">
        <text class="label">端口号</text>
        <input
          v-model="portNo"
          class="input"
          type="number"
          placeholder="可选，不填则下一步选择"
          placeholder-class="ph"
        />
      </view>
      <button class="btn-ghost submit" @tap="goConfirm">下一步</button>
      <view class="muted small form-tip">设备编号可在桩体铭牌或后台设备列表查看。</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import request, { showError } from '@/utils/request'
import { isScanCancel, scanDeviceCode } from '@/utils/deviceCode'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const deviceNo = ref('')
const portNo = ref('')

onLoad((options) => {
  if (options.deviceNo) deviceNo.value = options.deviceNo
})

async function startScan() {
  try {
    const parsed = await scanDeviceCode()
    deviceNo.value = parsed.deviceNo
    portNo.value = parsed.portNo
    await goConfirm()
  } catch (e) {
    if (isScanCancel(e)) return
    uni.showToast({ title: e.message || '当前环境不支持扫码，请手动输入', icon: 'none', duration: 2500 })
  }
}

async function goConfirm() {
  const code = String(deviceNo.value || '').trim().toUpperCase()
  if (!code) {
    uni.showToast({ title: '请输入设备编号', icon: 'none' })
    return
  }
  const port = String(portNo.value || '').trim()
  try {
    await userStore.ensureLogin()
    await request({ url: `/api/app/chargers/by-no/${encodeURIComponent(code)}` })
    let url = `/pages/charge/confirm?deviceNo=${encodeURIComponent(code)}`
    if (port) url += `&portNo=${port}`
    uni.navigateTo({ url })
  } catch (e) {
    if (e.code !== 401) showError(e)
  }
}
</script>

<style lang="scss" scoped>
.scan-card {
  padding: 40rpx 32rpx 34rpx;
  text-align: center;
}

.scan-frame {
  width: 330rpx;
  height: 330rpx;
  margin: 10rpx auto 0;
  border-radius: 28rpx;
  background: #f7f9f8;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.corner {
  position: absolute;
  width: 52rpx;
  height: 52rpx;
  border-color: #00a870;
  border-style: solid;
}

.corner.tl {
  top: 26rpx;
  left: 26rpx;
  border-width: 6rpx 0 0 6rpx;
  border-radius: 14rpx 0 0 0;
}

.corner.tr {
  top: 26rpx;
  right: 26rpx;
  border-width: 6rpx 6rpx 0 0;
  border-radius: 0 14rpx 0 0;
}

.corner.bl {
  bottom: 26rpx;
  left: 26rpx;
  border-width: 0 0 6rpx 6rpx;
  border-radius: 0 0 0 14rpx;
}

.corner.br {
  bottom: 26rpx;
  right: 26rpx;
  border-width: 0 6rpx 6rpx 0;
  border-radius: 0 0 14rpx 0;
}

.scan-beam {
  position: absolute;
  left: 56rpx;
  right: 56rpx;
  top: 50%;
  height: 4rpx;
  background: linear-gradient(90deg, transparent, #34d399, transparent);
  box-shadow: 0 0 18rpx rgba(52, 211, 153, 0.8);
}

.frame-hint {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 36rpx;
  text-align: center;
  font-size: 24rpx;
  color: #98a2b3;
}

.scan-tip {
  margin-top: 26rpx;
  font-size: 24rpx;
  color: #475467;
}

.scan-btn {
  margin-top: 32rpx;
}

.form-card {
  padding: 10rpx 30rpx 30rpx;
}

.field {
  padding: 26rpx 0;
  border-bottom: 2rpx solid #f5f7f6;
}

.label {
  font-size: 24rpx;
  color: #475467;
}

.input {
  margin-top: 14rpx;
  height: 64rpx;
  font-size: 32rpx;
  font-weight: 600;
}

.ph {
  color: #c6cdd5;
  font-weight: 400;
  font-size: 28rpx;
}

.submit {
  margin-top: 32rpx;
}

.form-tip {
  margin-top: 20rpx;
  text-align: center;
}
</style>

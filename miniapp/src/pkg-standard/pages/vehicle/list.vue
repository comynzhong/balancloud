<template>
  <view class="vehicle-page">
    <view class="page-head">
      <view>
        <view class="page-title">我的车辆</view>
        <view class="page-sub">绑定车辆后，充电订单可关联车牌，便于后台查询与对账。</view>
      </view>
      <view class="add-btn" @tap="openCreate">添加</view>
    </view>

    <view v-if="loading && list.length === 0" class="card muted small">正在加载车辆…</view>

    <view v-else-if="!list.length" class="card empty">
      <view class="empty-mark">车</view>
      <view class="empty-text">还没有绑定车辆</view>
      <button class="btn-primary empty-btn" @tap="openCreate">添加第一辆车</button>
    </view>

    <view v-for="item in list" :key="item.id" class="card vehicle-card">
      <view class="vehicle-head">
        <view class="plate-badge">
          <text class="plate num">{{ item.plateNo }}</text>
          <text v-if="item.isDefault === '1'" class="default-tag">默认</text>
        </view>
        <text class="vehicle-desc">{{ describe(item) }}</text>
      </view>
      <view class="vehicle-actions">
        <text v-if="item.isDefault !== '1'" class="action" @tap="setDefault(item)">设为默认</text>
        <text class="action" @tap="openEdit(item)">编辑</text>
        <text class="action danger" @tap="remove(item)">删除</text>
      </view>
    </view>

    <view class="tip-card card">
      <view class="tip-title">为什么要绑定车辆？</view>
      <view class="tip-line">· 充电前选择车辆，订单会自动记录车牌</view>
      <view class="tip-line">· 运营后台可按车牌检索订单，处理争议更高效</view>
      <view class="tip-line">· 车牌仅在当前账号下唯一，不影响其他用户</view>
      <view class="tip-line">· 填写 VIN 后可在插枪即充的交流桩上自动开始计费</view>
    </view>

    <view v-if="sheetVisible" class="sheet-mask" @tap="closeSheet">
      <view class="sheet" @tap.stop>
        <view class="sheet-head">
          <text class="sheet-title">{{ form.id ? '编辑车辆' : '添加车辆' }}</text>
          <text class="sheet-close" @tap="closeSheet">关闭</text>
        </view>

        <view class="field">
          <text class="field-label">车牌号<text class="required">*</text></text>
          <input v-model="form.plateNo" class="field-input num" maxlength="16" placeholder="例如 粤B12345" />
        </view>
        <view class="field">
          <text class="field-label">VIN</text>
          <input v-model="form.vin" class="field-input num" maxlength="17" placeholder="选填，17 位车架号" />
          <text class="field-hint">插枪即充的交流桩按 VIN 识别车主</text>
        </view>
        <view class="field">
          <text class="field-label">品牌</text>
          <input v-model="form.brand" class="field-input" maxlength="32" placeholder="选填，例如 比亚迪" />
        </view>
        <view class="field">
          <text class="field-label">车型</text>
          <input v-model="form.model" class="field-input" maxlength="64" placeholder="选填，例如 汉 EV" />
        </view>
        <view class="field">
          <text class="field-label">颜色</text>
          <input v-model="form.color" class="field-input" maxlength="16" placeholder="选填，例如 白色" />
        </view>
        <view class="field switch-field">
          <view>
            <text class="field-label">设为默认车辆</text>
            <text class="field-hint">充电时会自动选中</text>
          </view>
          <switch :checked="form.isDefault" color="#00a870" @change="onDefaultChange" />
        </view>

        <button class="btn-primary sheet-btn" :disabled="saving" @tap="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import request, { showError } from '@/utils/request'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const list = ref([])
const loading = ref(false)
const saving = ref(false)
const sheetVisible = ref(false)

const form = reactive({
  id: null,
  plateNo: '',
  vin: '',
  brand: '',
  model: '',
  color: '',
  isDefault: false
})

function describe(item) {
  const parts = [item.brand, item.model, item.color].filter(Boolean)
  return parts.length ? parts.join(' · ') : '未填写车型信息'
}

function resetForm() {
  form.id = null
  form.plateNo = ''
  form.vin = ''
  form.brand = ''
  form.model = ''
  form.color = ''
  form.isDefault = false
}

async function load() {
  if (loading.value) return
  loading.value = true
  try {
    await userStore.ensureLogin()
    list.value = (await request({ url: '/api/app/vehicles' })) || []
  } catch (e) {
    if (e.code !== 401) showError(e)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  resetForm()
  form.isDefault = !list.value.length
  sheetVisible.value = true
}

function openEdit(item) {
  form.id = item.id
  form.plateNo = item.plateNo || ''
  form.vin = item.vin || ''
  form.brand = item.brand || ''
  form.model = item.model || ''
  form.color = item.color || ''
  form.isDefault = item.isDefault === '1'
  sheetVisible.value = true
}

function closeSheet() {
  sheetVisible.value = false
}

function onDefaultChange(e) {
  form.isDefault = e.detail.value
}

async function save() {
  const plateNo = form.plateNo.trim()
  if (!plateNo) {
    uni.showToast({ title: '请输入车牌号', icon: 'none' })
    return
  }
  const vin = form.vin.trim().toUpperCase()
  if (vin && !/^[A-HJ-NPR-Z0-9]{17}$/.test(vin)) {
    uni.showToast({ title: 'VIN 必须是 17 位，且不含 I/O/Q', icon: 'none' })
    return
  }
  if (saving.value) return
  saving.value = true
  try {
    const payload = {
      plateNo,
      vin: vin || null,
      brand: form.brand.trim(),
      model: form.model.trim(),
      color: form.color.trim(),
      isDefault: form.isDefault
    }
    if (form.id) {
      await request({ url: `/api/app/vehicles/${form.id}`, method: 'PUT', data: payload })
    } else {
      await request({ url: '/api/app/vehicles', method: 'POST', data: payload })
    }
    uni.showToast({ title: '已保存', icon: 'success' })
    sheetVisible.value = false
    await load()
  } catch (e) {
    if (e.code !== 401) showError(e)
  } finally {
    saving.value = false
  }
}

function setDefault(item) {
  uni.showModal({
    title: '设为默认车辆',
    content: `将 ${item.plateNo} 设为默认车辆？`,
    confirmColor: '#00a870',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await request({ url: `/api/app/vehicles/${item.id}/default`, method: 'PUT' })
        await load()
      } catch (e) {
        if (e.code !== 401) showError(e)
      }
    }
  })
}

function remove(item) {
  uni.showModal({
    title: '删除车辆',
    content: `确定删除 ${item.plateNo}？历史订单不会受影响。`,
    confirmColor: '#d92d20',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await request({ url: `/api/app/vehicles/${item.id}`, method: 'DELETE' })
        uni.showToast({ title: '已删除', icon: 'none' })
        await load()
      } catch (e) {
        if (e.code !== 401) showError(e)
      }
    }
  })
}

onShow(load)
</script>

<style lang="scss" scoped>
.vehicle-page {
  min-height: 100vh;
  background: #f5f7f6;
  padding: 28rpx 32rpx calc(48rpx + env(safe-area-inset-bottom));
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.page-title {
  font-size: 38rpx;
  font-weight: 800;
  color: #101828;
}

.page-sub {
  margin-top: 8rpx;
  max-width: 480rpx;
  font-size: 20rpx;
  line-height: 1.6;
  color: #98a2b3;
}

.add-btn {
  flex: none;
  margin-top: 6rpx;
  padding: 12rpx 28rpx;
  border-radius: 999rpx;
  background: #00a870;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 600;
  box-shadow: 0 10rpx 22rpx -12rpx rgba(0, 168, 112, 0.9);
}

.vehicle-card {
  padding: 26rpx 28rpx;
  margin-bottom: 18rpx;
}

.vehicle-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.plate-badge {
  display: flex;
  align-items: center;
}

.plate {
  font-size: 34rpx;
  font-weight: 800;
  letter-spacing: 2rpx;
  color: #101828;
}

.default-tag {
  margin-left: 14rpx;
  padding: 2rpx 14rpx;
  border-radius: 999rpx;
  background: #e6f7f1;
  color: #00895c;
  font-size: 18rpx;
  font-weight: 600;
}

.vehicle-desc {
  font-size: 20rpx;
  color: #98a2b3;
  max-width: 46%;
  text-align: right;
}

.vehicle-actions {
  margin-top: 20rpx;
  padding-top: 18rpx;
  border-top: 2rpx dashed #edf0ee;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.action {
  font-size: 22rpx;
  color: #00895c;
  padding: 4rpx 18rpx;
}

.action.danger {
  color: #d92d20;
}

.empty {
  padding: 70rpx 24rpx;
  text-align: center;
}

.empty-mark {
  width: 96rpx;
  height: 96rpx;
  margin: 0 auto 20rpx;
  border-radius: 28rpx;
  background: #f3f7f5;
  color: #00895c;
  font-size: 40rpx;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
}

.empty-text {
  font-size: 24rpx;
  color: #98a2b3;
}

.empty-btn {
  margin: 32rpx auto 0;
  width: 360rpx;
  height: 78rpx;
  line-height: 78rpx;
  font-size: 26rpx;
}

.tip-card {
  margin-top: 26rpx;
  padding: 24rpx 28rpx;
  background: #f0faf6;
}

.tip-title {
  font-size: 24rpx;
  font-weight: 600;
  color: #00895c;
}

.tip-line {
  margin-top: 10rpx;
  font-size: 20rpx;
  line-height: 1.6;
  color: #5c8a78;
}

.sheet-mask {
  position: fixed;
  inset: 0;
  z-index: 20;
  background: rgba(16, 24, 40, 0.45);
  display: flex;
  align-items: flex-end;
}

.sheet {
  width: 100%;
  padding: 30rpx 32rpx calc(32rpx + env(safe-area-inset-bottom));
  border-radius: 36rpx 36rpx 0 0;
  background: #ffffff;
}

.sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.sheet-title {
  font-size: 30rpx;
  font-weight: 700;
}

.sheet-close {
  font-size: 22rpx;
  color: #98a2b3;
}

.field {
  margin-top: 22rpx;
}

.field-label {
  display: block;
  font-size: 22rpx;
  color: #475467;
}

.required {
  margin-left: 4rpx;
  color: #d92d20;
}

.field-input {
  margin-top: 10rpx;
  height: 78rpx;
  padding: 0 22rpx;
  border-radius: 18rpx;
  border: 2rpx solid #edf0ee;
  background: #f7f9f8;
  font-size: 26rpx;
  color: #101828;
}

.switch-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.field-hint {
  display: block;
  margin-top: 4rpx;
  font-size: 18rpx;
  color: #98a2b3;
}

.sheet-btn {
  margin-top: 34rpx;
  height: 84rpx;
  line-height: 84rpx;
  font-size: 28rpx;
}
</style>

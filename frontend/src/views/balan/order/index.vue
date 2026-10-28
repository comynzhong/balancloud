<template>
  <div class="app-container order-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">OPERATIONS · ORDER</p>
        <h2>充电订单</h2>
        <p class="page-desc">从启动建单到停止结算，平台作为计费唯一权威，订单保留启动时的费率快照与分时段费用明细。</p>
      </div>
      <div class="page-actions">
        <el-button icon="Refresh" @click="getList">刷新</el-button>
        <el-button v-hasPermi="['order:export']" type="primary" plain icon="Download" @click="handleExport">导出 Excel</el-button>
      </div>
    </div>

    <el-row :gutter="14" class="summary-row">
      <el-col v-for="item in summaryCards" :key="item.label" :xs="12" :sm="6">
        <el-card shadow="never" class="summary-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="76px">
        <el-form-item label="归属场站" prop="stationId">
          <el-select v-model="queryParams.stationId" placeholder="全部场站" clearable style="width: 190px">
            <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="订单号 / 设备 / 用户手机 / 车牌" clearable style="width: 220px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="订单状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="item in orderStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="支付状态" prop="paymentStatus">
          <el-select v-model="queryParams.paymentStatus" placeholder="全部" clearable style="width: 120px">
            <el-option v-for="item in paymentStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="toolbar">
        <el-col :span="1.5">
          <el-tag type="info" effect="plain">共 {{ total }} 笔订单</el-tag>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="orderList" row-key="id" @row-dblclick="openDetail">
        <el-table-column label="订单号" min-width="205">
          <template #default="{ row }">
            <span class="strong order-no">{{ row.orderNo }}</span>
            <div class="muted">{{ row.rateStrategyName }}</div>
          </template>
        </el-table-column>
        <el-table-column label="场站 / 设备" min-width="190">
          <template #default="{ row }">
            <div class="strong">{{ row.stationName }}</div>
            <div class="muted">{{ row.deviceNo }} · {{ row.portNo }} 号口</div>
          </template>
        </el-table-column>
        <el-table-column label="用户" width="150">
          <template #default="{ row }">
            <div>{{ row.appUserId ? (row.appUserNickname || '小程序用户') : '后台发起' }}</div>
            <div class="muted">{{ row.vehiclePlateNo || row.appUserPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="计费模式" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ billingModeLabel(row.billingMode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始 / 结束" min-width="185">
          <template #default="{ row }">
            <div>{{ formatTime(row.startTime) }}</div>
            <div class="muted">{{ row.endTime ? formatTime(row.endTime) : '进行中' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="时长" width="110" align="right">
          <template #default="{ row }">{{ formatDuration(row.durationSeconds) }}</template>
        </el-table-column>
        <el-table-column label="电量" width="100" align="right">
          <template #default="{ row }">{{ formatNumber(row.energyKwh, 3) }} kWh</template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <div class="money">{{ formatMoney(row.totalFee) }}</div>
            <div v-if="row.refundAmount" class="refund-note">已退 {{ formatMoney(row.refundAmount) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" width="105" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="orderStatusType(row.status)" effect="plain">{{ orderStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付状态" width="95" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="paymentStatusType(row.paymentStatus)" effect="plain">
              {{ paymentStatusLabel(row.paymentStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="停止原因" width="115">
          <template #default="{ row }">
            <span class="muted">{{ stopReasonLabel(row.stopReason) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="canPay(row)"
              v-hasPermi="['order:pay']"
              link
              type="success"
              icon="Wallet"
              @click="handlePay(row)"
            >支付</el-button>
            <el-button
              v-if="canRefund(row)"
              v-hasPermi="['order:refund']"
              link
              type="warning"
              icon="RefreshLeft"
              @click="openRefund(row)"
            >退款</el-button>
            <el-button
              v-if="canCorrectAmount(row)"
              v-hasPermi="['order:correctAmount']"
              link
              type="info"
              icon="EditPen"
              @click="openAmount(row)"
            >修正金额</el-button>
            <el-button
              v-if="canForceFinish(row)"
              v-hasPermi="['order:forceFinish']"
              link
              type="danger"
              icon="CircleClose"
              @click="handleForceFinish(row)"
            >强制结束</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <el-drawer v-model="detailVisible" size="76%" :destroy-on-close="true">
      <template #header>
        <div class="detail-header">
          <div>
            <div class="detail-title-line">
              <h3>{{ detail?.orderNo || '订单详情' }}</h3>
              <el-tag v-if="detail" size="small" :type="orderStatusType(detail.status)" effect="plain">
                {{ orderStatusLabel(detail.status) }}
              </el-tag>
              <el-tag v-if="detail" size="small" :type="paymentStatusType(detail.paymentStatus)" effect="plain">
                {{ paymentStatusLabel(detail.paymentStatus) }}
              </el-tag>
            </div>
            <p class="muted">{{ detail?.stationName }} · {{ detail?.deviceNo }} · {{ detail?.portNo }} 号口</p>
          </div>
          <div class="detail-actions">
            <el-button
              v-if="detail && canPay(detail)"
              v-hasPermi="['order:pay']"
              type="success"
              icon="Wallet"
              :loading="payLoading"
              @click="handlePay(detail)"
            >模拟支付</el-button>
            <el-button
              v-if="detail && canRefund(detail)"
              v-hasPermi="['order:refund']"
              type="warning"
              icon="RefreshLeft"
              @click="openRefund(detail)"
            >模拟退款</el-button>
            <el-button
              v-if="detail && canCorrectAmount(detail)"
              v-hasPermi="['order:correctAmount']"
              type="info"
              icon="EditPen"
              @click="openAmount(detail)"
            >修正金额</el-button>
            <el-button
              v-if="detail && canForceFinish(detail)"
              v-hasPermi="['order:forceFinish']"
              type="danger"
              icon="CircleClose"
              plain
              @click="handleForceFinish(detail)"
            >强制结束</el-button>
          </div>
        </div>
      </template>

      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <el-row :gutter="12" class="detail-stats">
            <el-col :xs="12" :sm="6">
              <div class="fee-stat highlight">
                <span>订单金额</span>
                <strong>{{ formatMoney(detail.totalFee) }}</strong>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="fee-stat">
                <span>充电电量</span>
                <strong>{{ formatNumber(detail.energyKwh, 3) }} <small>kWh</small></strong>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="fee-stat">
                <span>充电时长</span>
                <strong>{{ formatDuration(detail.durationSeconds) }}</strong>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="fee-stat">
                <span>停止原因</span>
                <strong class="reason">{{ stopReasonLabel(detail.stopReason) }}</strong>
              </div>
            </el-col>
          </el-row>

          <div class="section-title">基础信息</div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="计费模式">{{ billingModeLabel(detail.billingMode) }}</el-descriptions-item>
            <el-descriptions-item label="费率策略">{{ detail.rateStrategyName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="开始时间">{{ formatTime(detail.startTime) }}</el-descriptions-item>
            <el-descriptions-item label="结束时间">{{ formatTime(detail.endTime) }}</el-descriptions-item>
            <el-descriptions-item label="支付时间">{{ formatTime(detail.paidAt) }}</el-descriptions-item>
            <el-descriptions-item label="起始表底">{{ formatNumber(detail.startMeter, 4) }} kWh</el-descriptions-item>
            <el-descriptions-item label="结束表底">{{ formatNumber(detail.endMeter, 4) }} kWh</el-descriptions-item>
            <el-descriptions-item label="启动人">{{ detail.createBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="用户">
              <span v-if="detail.appUserId">
                {{ detail.appUserNickname || '小程序用户' }}
                <span v-if="detail.appUserPhone" class="muted"> · {{ detail.appUserPhone }}</span>
              </span>
              <span v-else class="muted">后台发起</span>
            </el-descriptions-item>
            <el-descriptions-item label="绑定车辆">{{ detail.vehiclePlateNo || '-' }}</el-descriptions-item>
            <el-descriptions-item label="时长上限">{{ detail.maxDurationMinutes }} 分钟</el-descriptions-item>
            <el-descriptions-item label="金额上限">{{ formatMoney(detail.chargeLimitAmount) }}</el-descriptions-item>
            <el-descriptions-item label="停止说明">{{ detail.stopMessage || '-' }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-title">费用构成</div>
          <el-row :gutter="12" class="fee-row">
            <el-col :xs="12" :sm="6">
              <div class="fee-cell"><span>时间费</span><strong>{{ formatMoney(detail.timeFee) }}</strong></div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="fee-cell"><span>电费</span><strong>{{ formatMoney(detail.energyFee) }}</strong></div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="fee-cell"><span>服务费</span><strong>{{ formatMoney(detail.serviceFee) }}</strong></div>
            </el-col>
            <el-col v-if="Number(detail.occupancyFee || 0) > 0" :xs="12" :sm="6">
              <div class="fee-cell">
                <span>占位费 · {{ Math.round(Number(detail.occupancySeconds || 0) / 60) }} 分钟</span>
                <strong>{{ formatMoney(detail.occupancyFee) }}</strong>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="fee-cell total"><span>合计</span><strong>{{ formatMoney(detail.totalFee) }}</strong></div>
            </el-col>
          </el-row>

          <div v-if="detail.refund" class="section-title">
            <span>退款信息</span>
            <el-tag size="small" type="warning" effect="plain">模拟退款 · 不调用微信支付</el-tag>
          </div>
          <el-descriptions v-if="detail.refund" :column="3" border size="small">
            <el-descriptions-item label="退款单号">{{ detail.refund.refundNo }}</el-descriptions-item>
            <el-descriptions-item label="退款金额">{{ formatMoney(detail.refund.amount) }}</el-descriptions-item>
            <el-descriptions-item label="退款状态">
              {{ detail.refund.status === 'SUCCESS' ? '已退款' : detail.refund.status }}
            </el-descriptions-item>
            <el-descriptions-item label="退款原因" :span="2">{{ detail.refund.reason || '-' }}</el-descriptions-item>
            <el-descriptions-item label="操作人">{{ detail.refund.operator || '-' }}</el-descriptions-item>
            <el-descriptions-item label="退款时间" :span="3">{{ formatTime(detail.refund.refundTime) }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-title">
            <span>人工处理记录</span>
            <el-tag v-if="manualLogs.length" size="small" type="warning" effect="plain">{{ manualLogs.length }} 条</el-tag>
          </div>
          <el-table v-loading="manualLoading" :data="manualLogs" size="small" border empty-text="暂无人工处理记录">
            <el-table-column label="操作类型" width="115" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="manualActionType(row.actionType)" effect="plain">
                  {{ manualActionLabel(row.actionType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态变化" width="150">
              <template #default="{ row }">
                {{ orderStatusLabel(row.beforeStatus) }} → {{ orderStatusLabel(row.afterStatus) }}
              </template>
            </el-table-column>
            <el-table-column label="金额变化" width="170" align="right">
              <template #default="{ row }">
                {{ formatMoney(row.beforeAmount) }} → {{ formatMoney(row.afterAmount) }}
              </template>
            </el-table-column>
            <el-table-column label="原因" min-width="200" prop="reason" show-overflow-tooltip />
            <el-table-column label="操作人" width="110" prop="operator" />
            <el-table-column label="时间" width="165">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>

          <div class="section-title">
            <span>分时段明细</span>
            <el-tag size="small" type="info" effect="plain">平台累计</el-tag>
          </div>
          <el-table :data="detail.details" size="small" border empty-text="订单尚未产生分时段明细">
            <el-table-column label="时段" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="periodTagType(row.periodType)" effect="plain">{{ periodTypeLabel(row.periodType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时长" min-width="110" align="right">
              <template #default="{ row }">{{ formatDuration(row.durationSeconds) }}</template>
            </el-table-column>
            <el-table-column label="电量 (kWh)" min-width="110" align="right">
              <template #default="{ row }">{{ formatNumber(row.energyKwh, 4) }}</template>
            </el-table-column>
            <el-table-column label="时间费" min-width="100" align="right">
              <template #default="{ row }">{{ formatMoney(row.timeFee) }}</template>
            </el-table-column>
            <el-table-column label="电费" min-width="100" align="right">
              <template #default="{ row }">{{ formatMoney(row.energyFee) }}</template>
            </el-table-column>
            <el-table-column label="服务费" min-width="100" align="right">
              <template #default="{ row }">{{ formatMoney(row.serviceFee) }}</template>
            </el-table-column>
            <el-table-column label="服务费单价" min-width="120" align="right">
              <template #default="{ row }">
                {{ formatNumber(row.servicePrice, 4) }} {{ row.serviceUnit === 'HOUR' ? '元/小时' : '元/kWh' }}
              </template>
            </el-table-column>
          </el-table>

          <div class="section-title">
            <span>费率快照</span>
            <el-tag size="small" type="info" effect="plain">订单启动时冻结</el-tag>
          </div>
          <el-table :data="detail.rateSnapshot?.periods || []" size="small" border empty-text="无费率快照">
            <el-table-column label="时段" width="90" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="periodTagType(row.periodType)" effect="plain">{{ periodTypeLabel(row.periodType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="时间窗" min-width="150">
              <template #default="{ row }">{{ snapshotWindow(row) }}</template>
            </el-table-column>
            <el-table-column label="时间费 (元/小时)" min-width="125" align="right">
              <template #default="{ row }">{{ formatNumber(row.timePrice, 4) }}</template>
            </el-table-column>
            <el-table-column label="电费 (元/kWh)" min-width="125" align="right">
              <template #default="{ row }">{{ formatNumber(row.energyPrice, 4) }}</template>
            </el-table-column>
            <el-table-column label="服务费" min-width="110" align="right">
              <template #default="{ row }">{{ formatNumber(row.servicePrice, 4) }}</template>
            </el-table-column>
            <el-table-column label="单位" width="110" align="center">
              <template #default="{ row }">{{ row.serviceUnit === 'HOUR' ? '元/小时' : '元/kWh' }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="refundVisible" title="模拟退款" width="460px" :close-on-click-modal="false">
      <div v-if="refundTarget" class="refund-dialog">
        <p class="refund-tip">模拟退款只更新订单支付状态并生成退款记录，不会调用微信支付退款接口。</p>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单号">{{ refundTarget.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="退款金额">
            <span class="money">{{ formatMoney(refundTarget.totalFee) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="退款原因">
            <el-input
              v-model="refundReason"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="选填，例如：用户申请退款 / 设备故障补偿"
            />
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="warning" :loading="refundLoading" @click="handleRefund">确认退款</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="amountVisible" title="金额修正" width="480px" :close-on-click-modal="false">
      <div v-if="amountTarget" class="amount-dialog">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="金额修正会覆盖订单费用并重算合计，请填写修正原因备查。"
        />
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单号" :span="2">{{ amountTarget.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="当前合计">{{ formatMoney(amountTarget.totalFee) }}</el-descriptions-item>
          <el-descriptions-item label="占位费">{{ formatMoney(amountTarget.occupancyFee) }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="90px">
          <el-form-item label="时间费">
            <el-input-number v-model="amountForm.timeFee" :min="0" :max="999999" :precision="2" :step="1" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="电费">
            <el-input-number v-model="amountForm.energyFee" :min="0" :max="999999" :precision="2" :step="1" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="服务费">
            <el-input-number v-model="amountForm.serviceFee" :min="0" :max="999999" :precision="2" :step="1" controls-position="right" style="width: 100%" />
          </el-form-item>
          <el-form-item label="重算合计">
            <span class="money">{{ formatMoney(amountTotal) }}</span>
            <span class="muted amount-tip">含占位费 {{ formatMoney(amountTarget.occupancyFee) }}</span>
          </el-form-item>
          <el-form-item label="修正原因" required>
            <el-input
              v-model="amountForm.reason"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="至少 4 个字符，例如：计费时段异常，按现场确认电量修正"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="amountVisible = false">取消</el-button>
        <el-button type="primary" :loading="amountLoading" @click="handleAmount">确认修正</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  correctOrderAmount,
  forceFinishOrder,
  getOrder,
  listOrder,
  listOrderManualLogs,
  payOrder,
  refundOrder
} from '@/api/balan/order'
import { getStationOptions } from '@/api/balan/station'
import { getDictOptions } from '@/api/balan/dict'
import {
  billingModeLabel,
  formatDuration,
  formatMoney,
  formatNumber,
  formatTime,
  orderStatusLabel,
  orderStatusOptions as defaultOrderStatusOptions,
  orderStatusType,
  paymentStatusLabel,
  paymentStatusOptions as defaultPaymentStatusOptions,
  paymentStatusType,
  periodTypeLabel,
  stopReasonLabel
} from '@/utils/balan'

defineOptions({ name: 'BalanOrder' })

const { proxy } = getCurrentInstance()
const route = useRoute()

const loading = ref(false)
const showSearch = ref(true)
const orderList = ref([])
const total = ref(0)
const stationOptions = ref([])
const orderStatusOptions = ref(defaultOrderStatusOptions)
const paymentStatusOptions = ref(defaultPaymentStatusOptions)
const queryRef = ref()
const dateRange = ref([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  stationId: undefined,
  keyword: undefined,
  status: undefined,
  paymentStatus: undefined,
  beginTime: undefined,
  endTime: undefined
})

const summaryCards = computed(() => {
  const rows = orderList.value
  const active = rows.filter(row => ['PENDING_START', 'CHARGING', 'STOPPING'].includes(row.status)).length
  const completed = rows.filter(row => row.status === 'COMPLETED' || row.status === 'ABNORMAL').length
  const paid = rows
    .filter(row => row.paymentStatus === 'PAID')
    .reduce((sum, row) => sum + Number(row.totalFee || 0), 0)
  return [
    { label: '订单总数', value: total.value, note: '当前查询范围' },
    { label: '进行中', value: active, note: '当前页' },
    { label: '已结束', value: completed, note: '当前页' },
    { label: '本页已收', value: formatMoney(paid), note: '当前页已支付订单' }
  ]
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const payLoading = ref(false)

const refundVisible = ref(false)
const refundTarget = ref(null)
const refundReason = ref('')
const refundLoading = ref(false)

const manualLogs = ref([])
const manualLoading = ref(false)

const amountVisible = ref(false)
const amountTarget = ref(null)
const amountLoading = ref(false)
const amountForm = reactive({
  timeFee: 0,
  energyFee: 0,
  serviceFee: 0,
  reason: ''
})

const amountTotal = computed(() => {
  const target = amountTarget.value
  const occupancy = Number(target?.occupancyFee || 0)
  return Number(amountForm.timeFee || 0) + Number(amountForm.energyFee || 0)
    + Number(amountForm.serviceFee || 0) + occupancy
})

function getList() {
  loading.value = true
  listOrder(queryParams).then(res => {
    orderList.value = res.rows || []
    total.value = res.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function loadStationOptions() {
  getStationOptions().then(res => {
    stationOptions.value = res.data || []
  })
}

function handleQuery() {
  queryParams.beginTime = dateRange.value?.[0]
  queryParams.endTime = dateRange.value?.[1]
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryRef.value?.resetFields()
  dateRange.value = []
  queryParams.beginTime = undefined
  queryParams.endTime = undefined
  queryParams.pageNum = 1
  getList()
}

function openDetail(row) {
  detailVisible.value = true
  refreshDetail(row.id)
}

function refreshDetail(id) {
  detailLoading.value = true
  detail.value = null
  getOrder(id).then(res => {
    detail.value = res.data
  }).finally(() => {
    detailLoading.value = false
  })
  loadManualLogs(id)
}

function loadManualLogs(id) {
  manualLoading.value = true
  listOrderManualLogs(id).then(res => {
    manualLogs.value = res.data || []
  }).catch(() => {
    manualLogs.value = []
  }).finally(() => {
    manualLoading.value = false
  })
}

function canPay(row) {
  return row && row.paymentStatus === 'UNPAID'
    && ['COMPLETED', 'ABNORMAL'].includes(row.status)
}

function canRefund(row) {
  return row && row.paymentStatus === 'PAID'
    && ['COMPLETED', 'ABNORMAL'].includes(row.status)
    && Number(row.totalFee || 0) > 0
}

function canForceFinish(row) {
  return row && ['PENDING_START', 'CHARGING', 'STOPPING'].includes(row.status)
}

function canCorrectAmount(row) {
  return row && ['COMPLETED', 'ABNORMAL'].includes(row.status)
    && row.paymentStatus !== 'REFUNDED'
}

function openRefund(row) {
  refundTarget.value = row
  refundReason.value = ''
  refundVisible.value = true
}

function openAmount(row) {
  amountTarget.value = row
  amountForm.timeFee = Number(row.timeFee || 0)
  amountForm.energyFee = Number(row.energyFee || 0)
  amountForm.serviceFee = Number(row.serviceFee || 0)
  amountForm.reason = ''
  amountVisible.value = true
}

function handleAmount() {
  if (!amountTarget.value) {
    return
  }
  const reason = amountForm.reason.trim()
  if (reason.length < 4) {
    ElMessage.warning('修正原因至少 4 个字符')
    return
  }
  const id = amountTarget.value.id
  amountLoading.value = true
  correctOrderAmount(id, {
    timeFee: amountForm.timeFee,
    energyFee: amountForm.energyFee,
    serviceFee: amountForm.serviceFee,
    reason
  }).then(() => {
    ElMessage.success('金额已修正')
    amountVisible.value = false
    if (detail.value && detail.value.id === id) {
      refreshDetail(id)
    }
    getList()
  }).finally(() => {
    amountLoading.value = false
  })
}

function handleForceFinish(row) {
  ElMessageBox.prompt(
    `订单「${row.orderNo}」正在进行中，强制结束会按最后测点结算并尝试下发停机，请填写处理原因（至少 4 个字符）。`,
    '强制结束订单',
    {
      type: 'warning',
      confirmButtonText: '确认结束',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '例如：设备离线，站点确认车辆已拔枪',
      inputValidator: value => (value && value.trim().length >= 4) || '处理原因至少 4 个字符'
    }
  ).then(({ value }) => {
    proxy.$modal.loading('正在强制结束订单...')
    return forceFinishOrder(row.id, { reason: value.trim() }).then(() => {
      ElMessage.success('订单已强制结束')
      if (detail.value && detail.value.id === row.id) {
        refreshDetail(row.id)
      }
      getList()
    }).finally(() => {
      proxy.$modal.closeLoading()
    })
  }).catch(() => {})
}

function manualActionLabel(action) {
  return { FORCE_FINISH: '强制结束', CORRECT_AMOUNT: '金额修正' }[action] || action || '-'
}

function manualActionType(action) {
  return { FORCE_FINISH: 'danger', CORRECT_AMOUNT: 'warning' }[action] || 'info'
}

function handleRefund() {
  if (!refundTarget.value) {
    return
  }
  refundLoading.value = true
  refundOrder(refundTarget.value.id, { reason: refundReason.value }).then(res => {
    ElMessage.success('退款完成')
    refundVisible.value = false
    if (detail.value && detail.value.id === refundTarget.value.id) {
      detail.value = res.data
    }
    getList()
  }).finally(() => {
    refundLoading.value = false
  })
}

function handlePay(row) {
  ElMessageBox.confirm(`确认对订单「${row.orderNo}」执行模拟支付 ${formatMoney(row.totalFee)} 吗？`, '模拟支付', {
    type: 'info',
    confirmButtonText: '确认支付',
    cancelButtonText: '取消'
  }).then(() => {
    payLoading.value = true
    return payOrder(row.id).then(res => {
      ElMessage.success('支付成功')
      if (detail.value && detail.value.id === row.id) {
        detail.value = res.data
      }
      getList()
    })
  }).catch(() => {}).finally(() => {
    payLoading.value = false
  })
}

function handleExport() {
  const params = {
    ...queryParams,
    beginTime: dateRange.value?.[0],
    endTime: dateRange.value?.[1]
  }
  proxy.download('/api/v1/orders/export', params, `充电订单_${new Date().getTime()}.xlsx`)
}

function periodTagType(type) {
  return { PEAK: 'danger', FLAT: 'warning', VALLEY: 'success' }[type] || 'info'
}

function snapshotWindow(period) {
  if (!period.startTime || !period.endTime) {
    return '-'
  }
  if (period.startTime === period.endTime) {
    return '全天'
  }
  return `${period.startTime} - ${period.endTime < period.startTime ? '次日 ' : ''}${period.endTime}`
}

function loadDictOptions() {
  Promise.all([getDictOptions('order_status'), getDictOptions('payment_status')])
    .then(([orderRes, payRes]) => {
      const statusOptions = (orderRes.data || []).map(item => ({ value: item.dictValue, label: item.dictLabel }))
      const payList = (payRes.data || []).map(item => ({ value: item.dictValue, label: item.dictLabel }))
      if (statusOptions.length) {
        orderStatusOptions.value = statusOptions
      }
      if (payList.length) {
        paymentStatusOptions.value = payList
      }
    })
    .catch(() => {})
}

onMounted(() => {
  if (route.query.keyword) {
    queryParams.keyword = String(route.query.keyword)
  }
  loadStationOptions()
  loadDictOptions()
  getList()
})
</script>

<style scoped lang="scss">
.order-page {
  .page-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 18px;
  }

  .page-kicker {
    margin: 0 0 6px;
    color: #0ea5e9;
    font-size: 11px;
    letter-spacing: 2px;
  }

  h2 {
    margin: 0;
    color: #0f172a;
    font-size: 24px;
  }

  .page-desc {
    margin: 8px 0 0;
    color: #64748b;
  }

  .page-actions {
    display: flex;
    gap: 10px;
  }

  .summary-row {
    margin-bottom: 14px;
  }

  .summary-card {
    border: 1px solid #e2e8f0;
    border-radius: 12px;

    :deep(.el-card__body) {
      display: flex;
      flex-direction: column;
      gap: 4px;
      padding: 16px 18px;
    }

    span {
      color: #64748b;
      font-size: 13px;
    }

    strong {
      color: #0f172a;
      font-size: 24px;
      line-height: 1.2;
    }

    small {
      color: #94a3b8;
      font-size: 12px;
    }
  }

  .table-card {
    border: 1px solid #e2e8f0;
    border-radius: 12px;
  }

  .toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  .strong {
    color: #0f172a;
    font-weight: 600;
  }

  .muted {
    color: #94a3b8;
    font-size: 12px;
  }

  .order-no {
    font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
    font-size: 13px;
  }

  .money {
    color: #0f172a;
    font-weight: 700;
  }

  .refund-note {
    color: #d97706;
    font-size: 12px;
  }

  .detail-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    flex-shrink: 0;
  }

  .refund-dialog {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .refund-tip {
    margin: 0;
    padding: 10px 12px;
    border-radius: 8px;
    background: #fffbeb;
    color: #b45309;
    font-size: 12px;
    line-height: 1.6;
  }

  .amount-dialog {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }

  .amount-tip {
    margin-left: 10px;
  }

  .detail-header {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px 20px;
    width: 100%;
  }

  .detail-title-line {
    display: flex;
    align-items: center;
    gap: 8px;

    h3 {
      margin: 0;
      color: #0f172a;
      font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
      font-size: 18px;
    }
  }

  .detail-header p {
    margin: 6px 0 0;
  }

  .detail-body {
    padding: 0 4px 24px;
  }

  .detail-stats {
    margin-bottom: 18px;
  }

  .fee-stat {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 14px 16px;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    background: #f8fafc;

    span {
      color: #64748b;
      font-size: 12px;
    }

    strong {
      color: #0f172a;
      font-size: 22px;

      small {
        color: #94a3b8;
        font-size: 12px;
        font-weight: 400;
      }
    }

    &.highlight {
      border-color: #bae6fd;
      background: linear-gradient(180deg, #f0f9ff, #fff);

      strong {
        color: #0284c7;
      }
    }

    .reason {
      font-size: 16px;
    }
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 18px 0 10px;
    color: #0f172a;
    font-weight: 600;
  }

  .fee-row {
    margin-bottom: 4px;
  }

  .fee-cell {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 14px;
    border: 1px solid #e2e8f0;
    border-radius: 8px;

    span {
      color: #64748b;
      font-size: 13px;
    }

    strong {
      color: #0f172a;
      font-size: 16px;
    }

    &.total {
      border-color: #bae6fd;
      background: #f0f9ff;

      strong {
        color: #0284c7;
      }
    }
  }
}
</style>

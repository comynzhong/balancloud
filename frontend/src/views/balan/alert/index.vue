<template>
  <div class="app-container alert-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">OPERATIONS · ALERT</p>
        <h2>告警记录</h2>
        <p class="page-desc">平台阈值判断与设备主动上报统一入库，60 秒内同源告警去重，阈值恢复后自动关闭。</p>
      </div>
      <div class="page-actions">
        <el-tag :type="live ? 'success' : 'info'" effect="plain" class="live-tag">
          <span class="live-dot" />{{ live ? '实时推送中' : '定时刷新（10 秒）' }}
        </el-tag>
        <el-button icon="Refresh" @click="refreshAll">刷新</el-button>
        <el-button v-hasPermi="['alert:list']" type="primary" plain icon="Download" @click="handleExport">导出 Excel</el-button>
      </div>
    </div>

    <el-row :gutter="14" class="summary-row">
      <el-col v-for="item in summaryCards" :key="item.label" :xs="12" :sm="6">
        <el-card shadow="never" class="summary-card" :class="item.tone">
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
        <el-form-item label="告警类型" prop="alertType">
          <el-select v-model="queryParams.alertType" placeholder="全部类型" clearable style="width: 150px">
            <el-option v-for="item in alertTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="告警级别" prop="alertLevel">
          <el-select v-model="queryParams.alertLevel" placeholder="全部级别" clearable style="width: 130px">
            <el-option v-for="item in alertLevelOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option v-for="item in alertStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="设备编号 / 告警内容" clearable style="width: 200px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="发生时间">
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
          <el-tag type="info" effect="plain">共 {{ total }} 条告警</el-tag>
        </el-col>
        <el-col :span="1.5">
          <el-checkbox v-model="onlyActive" @change="handleQuery">只看未闭环</el-checkbox>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="refreshAll" />
      </el-row>

      <el-table v-loading="loading" :data="alertList" row-key="id" @row-dblclick="openDetail">
        <el-table-column label="场站 / 设备" min-width="190">
          <template #default="{ row }">
            <div class="strong">{{ row.stationName || '-' }}</div>
            <div class="muted">{{ row.deviceNo }}<template v-if="row.portNo"> · {{ row.portNo }} 号口</template></div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ alertTypeLabel(row.alertType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="alertLevelType(row.alertLevel)" effect="dark">{{ alertLevelLabel(row.alertLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="告警内容" min-width="230">
          <template #default="{ row }">
            <div>{{ row.content }}</div>
            <div v-if="row.metricValue !== null && row.metricValue !== undefined" class="muted">
              实测 {{ formatNumber(row.metricValue, 2) }}
              <template v-if="row.thresholdValue !== null && row.thresholdValue !== undefined">
                · 阈值 {{ formatNumber(row.thresholdValue, 2) }}
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="来源" width="100" align="center">
          <template #default="{ row }">
            <span class="muted">{{ alertSourceLabel(row.source) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="发生 / 最近" min-width="175">
          <template #default="{ row }">
            <div>{{ formatTime(row.occurredAt) }}</div>
            <div class="muted">{{ formatTime(row.lastOccurredAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="alertStatusType(row.status)" effect="plain">{{ alertStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" min-width="130">
          <template #default="{ row }">
            <div v-if="row.acknowledgedBy" class="muted">{{ row.acknowledgedBy }} 确认</div>
            <div v-if="row.resolvedBy" class="muted">{{ row.resolvedBy }} 恢复</div>
            <span v-if="!row.acknowledgedBy && !row.resolvedBy" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="工单" width="150" align="center">
          <template #default="{ row }">
            <el-button v-if="row.workOrderId" link type="primary" @click="openWorkOrder(row)">
              {{ row.workOrderNo }}
            </el-button>
            <el-button
              v-else-if="row.status !== 'RESOLVED'"
              v-hasPermi="['workorder:add']"
              link
              type="warning"
              icon="DocumentAdd"
              @click="handleCreateWorkOrder(row)"
            >生成工单</el-button>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'OPEN'"
              v-hasPermi="['alert:ack']"
              link
              type="warning"
              icon="Check"
              @click="handleAck(row)"
            >确认</el-button>
            <el-button
              v-if="row.status !== 'RESOLVED'"
              v-hasPermi="['alert:resolve']"
              link
              type="success"
              icon="CircleCheck"
              @click="handleResolve(row)"
            >恢复</el-button>
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

    <el-drawer v-model="detailVisible" size="42%" :destroy-on-close="true">
      <template #header>
        <div class="detail-header">
          <div>
            <div class="detail-title-line">
              <h3>{{ detail ? alertTypeLabel(detail.alertType) : '告警详情' }}</h3>
              <el-tag v-if="detail" size="small" :type="alertLevelType(detail.alertLevel)" effect="dark">
                {{ alertLevelLabel(detail.alertLevel) }}
              </el-tag>
              <el-tag v-if="detail" size="small" :type="alertStatusType(detail.status)" effect="plain">
                {{ alertStatusLabel(detail.status) }}
              </el-tag>
            </div>
            <p class="muted">{{ detail?.stationName }} · {{ detail?.deviceNo }}{{ detail?.portNo ? ` · ${detail.portNo} 号口` : '' }}</p>
          </div>
          <div v-if="detail" class="detail-actions">
            <el-button
              v-if="detail.workOrderId"
              type="primary"
              plain
              icon="Document"
              @click="openWorkOrder(detail)"
            >查看工单</el-button>
            <el-button
              v-else-if="detail.status !== 'RESOLVED'"
              v-hasPermi="['workorder:add']"
              type="primary"
              plain
              icon="DocumentAdd"
              @click="handleCreateWorkOrder(detail)"
            >生成工单</el-button>
            <el-button
              v-if="detail.status === 'OPEN'"
              v-hasPermi="['alert:ack']"
              type="warning"
              icon="Check"
              :loading="actionLoading"
              @click="handleAck(detail)"
            >确认告警</el-button>
            <el-button
              v-if="detail.status !== 'RESOLVED'"
              v-hasPermi="['alert:resolve']"
              type="success"
              icon="CircleCheck"
              :loading="actionLoading"
              @click="handleResolve(detail)"
            >标记恢复</el-button>
          </div>
        </div>
      </template>

      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <div class="section-title">告警信息</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="告警内容">{{ detail.content || '-' }}</el-descriptions-item>
            <el-descriptions-item label="判定来源">{{ alertSourceLabel(detail.source) }}</el-descriptions-item>
            <el-descriptions-item label="实测值">
              {{ detail.metricValue === null || detail.metricValue === undefined ? '-' : formatNumber(detail.metricValue, 2) }}
            </el-descriptions-item>
            <el-descriptions-item label="阈值">
              {{ detail.thresholdValue === null || detail.thresholdValue === undefined ? '-' : formatNumber(detail.thresholdValue, 2) }}
            </el-descriptions-item>
            <el-descriptions-item label="首次发生">{{ formatTime(detail.occurredAt) }}</el-descriptions-item>
            <el-descriptions-item label="最近发生">{{ formatTime(detail.lastOccurredAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-title">处理记录</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="当前状态">{{ alertStatusLabel(detail.status) }}</el-descriptions-item>
            <el-descriptions-item label="关联工单">{{ detail.workOrderNo || '未生成' }}</el-descriptions-item>
            <el-descriptions-item label="确认人">{{ detail.acknowledgedBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="确认时间">{{ formatTime(detail.acknowledgedAt) }}</el-descriptions-item>
            <el-descriptions-item label="恢复人">{{ detail.resolvedBy || '-' }}</el-descriptions-item>
            <el-descriptions-item label="恢复时间">{{ formatTime(detail.resolvedAt) }}</el-descriptions-item>
          </el-descriptions>

          <div v-if="detail.alertType === 'PORT_FAULT'" class="tip">端口故障需现场确认，平台不会自动恢复。</div>
          <div v-else class="tip">阈值类告警在测点连续 60 秒恢复正常后由系统自动关闭。</div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ackAlert, getAlert, listAlert, resolveAlert } from '@/api/balan/alert'
import { addWorkOrderFromAlert } from '@/api/balan/workorder'
import { getAlertStats } from '@/api/balan/dashboard'
import { getStationOptions } from '@/api/balan/station'
import { subscribeEventStream } from '@/utils/eventStream'
import {
  alertLevelLabel,
  alertLevelOptions,
  alertLevelType,
  alertSourceLabel,
  alertStatusLabel,
  alertStatusOptions,
  alertStatusType,
  alertTypeLabel,
  alertTypeOptions,
  formatNumber,
  formatTime
} from '@/utils/balan'

defineOptions({ name: 'BalanAlert' })

const { proxy } = getCurrentInstance()
const router = useRouter()
const FALLBACK_INTERVAL = 10000
const LIVE_DEBOUNCE = 1500

const loading = ref(false)
const showSearch = ref(true)
const live = ref(false)
const onlyActive = ref(false)
const alertList = ref([])
const total = ref(0)
const stationOptions = ref([])
const stats = ref({ todayTotal: 0, openCount: 0, acknowledgedCount: 0 })
const queryRef = ref()
const dateRange = ref([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  stationId: undefined,
  alertType: undefined,
  alertLevel: undefined,
  status: undefined,
  keyword: undefined,
  beginTime: undefined,
  endTime: undefined
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const actionLoading = ref(false)

let unsubscribe = null
let liveTimer = null
let fallbackTimer = null

const summaryCards = computed(() => [
  { label: '今日新增', value: stats.value.todayTotal ?? 0, note: '含设备上报与平台判定' },
  { label: '未处理', value: stats.value.openCount ?? 0, note: '等待运维确认', tone: 'danger' },
  { label: '已确认', value: stats.value.acknowledgedCount ?? 0, note: '处理中未闭环', tone: 'warning' },
  { label: '当前查询', value: total.value, note: onlyActive.value ? '仅未闭环告警' : '全部告警' }
])

function getList() {
  loading.value = true
  const params = { ...queryParams }
  if (onlyActive.value) {
    params.status = 'ACTIVE'
  }
  return listAlert(params).then(res => {
    alertList.value = res.rows || []
    total.value = res.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function getStats() {
  return getAlertStats({ stationId: queryParams.stationId }).then(res => {
    stats.value = res.data || {}
  })
}

function refreshAll() {
  return Promise.all([getList(), getStats()])
}

function handleQuery() {
  queryParams.beginTime = dateRange.value?.[0]
  queryParams.endTime = dateRange.value?.[1]
  queryParams.pageNum = 1
  refreshAll()
}

function resetQuery() {
  queryRef.value?.resetFields()
  dateRange.value = []
  onlyActive.value = false
  queryParams.beginTime = undefined
  queryParams.endTime = undefined
  queryParams.pageNum = 1
  refreshAll()
}

function handleExport() {
  const params = {
    ...queryParams,
    status: onlyActive.value ? 'ACTIVE' : queryParams.status,
    beginTime: dateRange.value?.[0],
    endTime: dateRange.value?.[1]
  }
  proxy.download('/api/v1/alerts/export', params, `告警记录_${new Date().getTime()}.xlsx`)
}

function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  getAlert(row.id).then(res => {
    detail.value = res.data
  }).finally(() => {
    detailLoading.value = false
  })
}

function handleAck(row) {
  ElMessageBox.confirm(`确认告警「${alertTypeLabel(row.alertType)} · ${row.content || ''}」吗？`, '确认告警', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  }).then(() => {
    actionLoading.value = true
    return ackAlert(row.id).then(res => {
      ElMessage.success('已确认告警')
      syncDetail(res.data)
      refreshAll()
    })
  }).catch(() => {}).finally(() => {
    actionLoading.value = false
  })
}

function handleResolve(row) {
  ElMessageBox.confirm(`标记告警「${alertTypeLabel(row.alertType)}」为已恢复吗？`, '标记恢复', {
    type: 'info',
    confirmButtonText: '标记恢复',
    cancelButtonText: '取消'
  }).then(() => {
    actionLoading.value = true
    return resolveAlert(row.id).then(res => {
      ElMessage.success('告警已恢复')
      syncDetail(res.data)
      refreshAll()
    })
  }).catch(() => {}).finally(() => {
    actionLoading.value = false
  })
}

function syncDetail(updated) {
  if (detail.value && updated && detail.value.id === updated.id) {
    detail.value = updated
  }
}

function openWorkOrder(row) {
  const workOrderId = row.workOrderId || detail.value?.workOrderId
  if (workOrderId) {
    router.push({ path: '/alert/work-order', query: { id: workOrderId } })
  }
}

function handleCreateWorkOrder(row) {
  const target = row || detail.value
  if (!target) {
    return
  }
  const title = `${alertTypeLabel(target.alertType)} · ${target.deviceNo || ''}${target.portNo ? ' · ' + target.portNo + ' 号口' : ''}`
  ElMessageBox.confirm(`按告警信息生成运维工单「${title}」？可在工单中继续指派与记录处理过程。`, '生成工单', {
    type: 'info',
    confirmButtonText: '生成工单',
    cancelButtonText: '取消'
  })
    .then(() => addWorkOrderFromAlert(target.id))
    .then(res => {
      const created = res.data
      ElMessage.success(`工单 ${created?.workOrderNo || ''} 已生成`)
      if (detail.value && created?.alertId === detail.value.id) {
        detail.value.workOrderId = created.id
        detail.value.workOrderNo = created.workOrderNo
        detail.value.workOrderStatus = created.status
      }
      refreshAll()
    })
    .catch(() => {})
}

function scheduleLiveRefresh() {
  if (liveTimer) {
    return
  }
  liveTimer = setTimeout(() => {
    liveTimer = null
    refreshAll()
  }, LIVE_DEBOUNCE)
}

function setLive(connected) {
  live.value = connected
  if (fallbackTimer) {
    clearInterval(fallbackTimer)
    fallbackTimer = null
  }
  if (!connected) {
    fallbackTimer = setInterval(refreshAll, FALLBACK_INTERVAL)
  }
}

onMounted(() => {
  getStationOptions().then(res => {
    stationOptions.value = res.data || []
  })
  refreshAll()
  unsubscribe = subscribeEventStream({ alert: scheduleLiveRefresh }, setLive)
})

onBeforeUnmount(() => {
  unsubscribe?.()
  if (liveTimer) {
    clearTimeout(liveTimer)
  }
  if (fallbackTimer) {
    clearInterval(fallbackTimer)
  }
})
</script>

<style scoped lang="scss">
.alert-page {
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
    align-items: center;
    gap: 10px;
  }

  .live-tag {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .live-dot {
    display: inline-block;
    width: 7px;
    height: 7px;
    margin-right: 2px;
    border-radius: 50%;
    background: currentColor;
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

    &.danger strong {
      color: #dc2626;
    }

    &.warning strong {
      color: #d97706;
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

  .detail-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    width: 100%;
  }

  .detail-title-line {
    display: flex;
    align-items: center;
    gap: 8px;

    h3 {
      margin: 0;
      color: #0f172a;
      font-size: 18px;
    }
  }

  .detail-header p {
    margin: 6px 0 0;
  }

  .detail-actions {
    display: flex;
    gap: 8px;
  }

  .detail-body {
    padding: 0 4px 24px;
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: 10px;
    margin: 18px 0 10px;
    color: #0f172a;
    font-weight: 600;
  }

  .tip {
    margin-top: 16px;
    padding: 10px 12px;
    border-radius: 8px;
    background: #f8fafc;
    color: #475569;
    font-size: 12px;
    line-height: 1.6;
  }
}
</style>

<template>
  <div class="app-container refund-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">ORDER · REFUND</p>
        <h2>退款记录</h2>
        <p class="page-desc">演示环境使用模拟退款：只更新订单支付状态并记录退款单，不调用微信支付退款接口。</p>
      </div>
      <div class="page-actions">
        <el-tag type="warning" effect="plain">模拟退款</el-tag>
        <el-button icon="Refresh" @click="getList">刷新</el-button>
        <el-button v-hasPermi="['order:export']" type="primary" plain icon="Download" @click="handleExport">导出 Excel</el-button>
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
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="订单号 / 用户手机 / 车牌" clearable style="width: 220px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="退款时间">
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
          <el-tag type="info" effect="plain">共 {{ total }} 笔退款</el-tag>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="refundList" row-key="id" empty-text="暂无退款记录">
        <el-table-column label="退款单号" min-width="205">
          <template #default="{ row }">
            <span class="strong refund-no">{{ row.refundNo || '-' }}</span>
            <div class="muted">{{ row.orderNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="场站 / 设备" min-width="180">
          <template #default="{ row }">
            <div class="strong">{{ row.stationName || '-' }}</div>
            <div class="muted">{{ row.deviceNo }} · {{ row.portNo }} 号口</div>
          </template>
        </el-table-column>
        <el-table-column label="用户 / 车辆" width="160">
          <template #default="{ row }">
            <div>{{ row.appUserId ? (row.appUserNickname || '小程序用户') : '后台发起' }}</div>
            <div class="muted">{{ row.vehiclePlateNo || row.appUserPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="订单金额" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalFee) }}</template>
        </el-table-column>
        <el-table-column label="退款金额" width="110" align="right">
          <template #default="{ row }">
            <span class="refund-amount">{{ formatMoney(row.refundAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="退款原因" min-width="200">
          <template #default="{ row }">
            <span>{{ row.refundReason || '运营后台模拟退款' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="退款时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.refundTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="openOrder(row)">查看订单</el-button>
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
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listOrder } from '@/api/balan/order'
import { getStationOptions } from '@/api/balan/station'
import { formatMoney, formatTime } from '@/utils/balan'

defineOptions({ name: 'BalanOrderRefund' })

const { proxy } = getCurrentInstance()
const router = useRouter()
const loading = ref(false)
const showSearch = ref(true)
const refundList = ref([])
const total = ref(0)
const stationOptions = ref([])
const queryRef = ref()
const dateRange = ref([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  stationId: undefined,
  keyword: undefined,
  paymentStatus: 'REFUNDED',
  refundBeginTime: undefined,
  refundEndTime: undefined
})

const summaryCards = computed(() => {
  const rows = refundList.value
  const amount = rows.reduce((sum, row) => sum + Number(row.refundAmount || 0), 0)
  const orders = new Set(rows.map(row => row.orderNo)).size
  return [
    { label: '退款笔数', value: total.value, note: '当前查询范围', tone: 'warning' },
    { label: '本页退款金额', value: formatMoney(amount), note: '当前页合计', tone: 'warning' },
    { label: '涉及订单', value: orders, note: '当前页去重' },
    { label: '退款方式', value: '模拟', note: '未调用微信支付' }
  ]
})

function getList() {
  loading.value = true
  listOrder(queryParams).then(res => {
    refundList.value = res.rows || []
    total.value = res.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function handleQuery() {
  queryParams.refundBeginTime = dateRange.value?.[0]
  queryParams.refundEndTime = dateRange.value?.[1]
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryRef.value?.resetFields()
  dateRange.value = []
  queryParams.refundBeginTime = undefined
  queryParams.refundEndTime = undefined
  queryParams.pageNum = 1
  getList()
}

function openOrder(row) {
  router.push({ path: '/order/list', query: { keyword: row.orderNo } })
}

function handleExport() {
  const params = {
    stationId: queryParams.stationId,
    keyword: queryParams.keyword,
    paymentStatus: 'REFUNDED',
    refundBeginTime: dateRange.value?.[0],
    refundEndTime: dateRange.value?.[1]
  }
  proxy.download('/api/v1/orders/refunds/export', params, `退款记录_${new Date().getTime()}.xlsx`)
}

onMounted(() => {
  getStationOptions().then(res => {
    stationOptions.value = res.data || []
  })
  getList()
})
</script>

<style scoped lang="scss">
.refund-page {
  .page-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 18px;
  }

  .page-kicker {
    margin: 0 0 6px;
    color: #d97706;
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

  .refund-no {
    font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
    font-size: 13px;
  }

  .refund-amount {
    color: #d97706;
    font-weight: 700;
  }
}
</style>

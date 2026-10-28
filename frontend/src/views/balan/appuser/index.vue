<template>
  <div class="app-container appuser-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">OPERATIONS · MINI PROGRAM USER</p>
        <h2>小程序用户</h2>
        <p class="page-desc">查看小程序注册用户、充电时长与消费情况；运营账号只展示本场站范围内有订单的用户。</p>
      </div>
      <div class="page-actions">
        <el-button icon="Refresh" @click="getList">刷新</el-button>
        <el-button v-hasPermi="['appuser:list']" type="primary" plain icon="Download" @click="handleExport">导出 Excel</el-button>
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
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="昵称 / 手机号" clearable style="width: 220px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="账号状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="toolbar">
        <el-col :span="1.5">
          <el-tag type="info" effect="plain">共 {{ total }} 位用户</el-tag>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="userList" row-key="id" @row-dblclick="openDetail">
        <el-table-column label="用户" min-width="190">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="36" :src="row.avatar || undefined">{{ (row.nickname || '用').slice(0, 1) }}</el-avatar>
              <div>
                <div class="strong">{{ row.nickname || '未设置昵称' }}</div>
                <div class="muted">{{ row.phone || '无手机号' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '0' ? 'success' : 'danger'" effect="plain">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前充电" min-width="210">
          <template #default="{ row }">
            <template v-if="row.activeOrderNo">
              <div class="strong">
                {{ row.activeStationName || '-' }} · {{ row.activePortNo }} 号口
              </div>
              <div class="muted">
                已充 {{ formatDuration(row.activeDurationSeconds) }} · 开始于 {{ formatTime(row.activeStartTime) }}
              </div>
            </template>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="订单" width="90" align="center">
          <template #default="{ row }">{{ row.orderCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="已支付" width="110" align="right">
          <template #default="{ row }">{{ formatMoney(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column label="已退款" width="110" align="right">
          <template #default="{ row }">
            <span :class="{ 'refund-amount': Number(row.refundedAmount || 0) > 0 }">
              {{ formatMoney(row.refundedAmount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="车辆" width="80" align="center">
          <template #default="{ row }">{{ row.vehicleCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="最近订单" min-width="165">
          <template #default="{ row }">{{ formatTime(row.lastOrderTime) }}</template>
        </el-table-column>
        <el-table-column label="最近登录" min-width="165">
          <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="openDetail(row)">详情</el-button>
            <el-button
              v-hasPermi="['appuser:edit']"
              link
              :type="row.status === '0' ? 'danger' : 'success'"
              :icon="row.status === '0' ? 'CircleClose' : 'CircleCheck'"
              @click="toggleStatus(row)"
            >{{ row.status === '0' ? '停用' : '启用' }}</el-button>
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

    <el-drawer v-model="detailVisible" size="64%" :destroy-on-close="true">
      <template #header>
        <div class="detail-header">
          <div>
            <div class="detail-title-line">
              <h3>{{ detail?.nickname || '用户详情' }}</h3>
              <el-tag v-if="detail" size="small" :type="detail.status === '0' ? 'success' : 'danger'" effect="plain">
                {{ detail.status === '0' ? '正常' : '停用' }}
              </el-tag>
            </div>
            <p class="muted">{{ detail?.phone || '无手机号' }}</p>
          </div>
          <el-button
            v-if="detail"
            v-hasPermi="['appuser:edit']"
            :type="detail.status === '0' ? 'danger' : 'success'"
            :icon="detail.status === '0' ? 'CircleClose' : 'CircleCheck'"
            @click="toggleStatus(detail)"
          >{{ detail.status === '0' ? '停用账号' : '启用账号' }}</el-button>
        </div>
      </template>

      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <el-row :gutter="12" class="detail-stats">
            <el-col :xs="12" :sm="3">
              <div class="stat-cell"><span>订单数</span><strong>{{ detail.orderCount || 0 }}</strong></div>
            </el-col>
            <el-col :xs="12" :sm="3">
              <div class="stat-cell"><span>已支付</span><strong>{{ formatMoney(detail.paidAmount) }}</strong></div>
            </el-col>
            <el-col :xs="12" :sm="3">
              <div class="stat-cell"><span>已退款</span><strong>{{ formatMoney(detail.refundedAmount) }}</strong></div>
            </el-col>
            <el-col :xs="12" :sm="3">
              <div class="stat-cell"><span>车辆数</span><strong>{{ detail.vehicleCount || 0 }}</strong></div>
            </el-col>
            <el-col :xs="24" :sm="12">
              <div class="stat-cell" :class="{ highlight: detail.activeOrderNo }">
                <span>当前充电</span>
                <strong v-if="detail.activeOrderNo" class="active-info">
                  {{ detail.activeStationName || '-' }} · {{ detail.activePortNo }} 号口
                  <small>已充 {{ formatDuration(detail.activeDurationSeconds) }}</small>
                </strong>
                <strong v-else>未在充电</strong>
              </div>
            </el-col>
          </el-row>

          <div class="section-title">基础信息</div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="用户 ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="注册时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="最近登录">{{ formatTime(detail.lastLoginAt) }}</el-descriptions-item>
            <el-descriptions-item label="最近订单" :span="3">{{ formatTime(detail.lastOrderTime) }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-title">
            <span>绑定车辆</span>
            <el-tag size="small" type="info" effect="plain">{{ detail.vehicles?.length || 0 }} 辆</el-tag>
          </div>
          <el-table :data="detail.vehicles || []" size="small" border empty-text="该用户尚未绑定车辆">
            <el-table-column label="车牌号" min-width="120">
              <template #default="{ row }">
                <span class="strong">{{ row.plateNo }}</span>
                <el-tag v-if="row.isDefault === '1'" size="small" type="success" effect="plain" class="default-tag">默认</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="品牌 / 车型" min-width="160">
              <template #default="{ row }">{{ [row.brand, row.model].filter(Boolean).join(' ') || '-' }}</template>
            </el-table-column>
            <el-table-column label="颜色" width="90">
              <template #default="{ row }">{{ row.color || '-' }}</template>
            </el-table-column>
            <el-table-column label="绑定时间" min-width="165">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
          </el-table>

          <div class="section-title">
            <span>最近订单</span>
            <el-tag size="small" type="info" effect="plain">最多 10 笔</el-tag>
          </div>
          <el-table :data="detail.recentOrders || []" size="small" border empty-text="暂无订单">
            <el-table-column label="订单号" min-width="195">
              <template #default="{ row }">
                <el-button link type="primary" @click="openOrder(row)">{{ row.orderNo }}</el-button>
              </template>
            </el-table-column>
            <el-table-column label="场站 / 端口" min-width="150">
              <template #default="{ row }">{{ row.stationName || '-' }} · {{ row.portNo }} 号口</template>
            </el-table-column>
            <el-table-column label="电量" width="100" align="right">
              <template #default="{ row }">{{ formatNumber(row.energyKwh, 3) }} kWh</template>
            </el-table-column>
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">{{ formatMoney(row.totalFee) }}</template>
            </el-table-column>
            <el-table-column label="订单状态" width="100" align="center">
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
            <el-table-column label="开始时间" min-width="165">
              <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changeAppUserStatus, getAppUser, listAppUser } from '@/api/balan/appuser'
import {
  formatDuration,
  formatMoney,
  formatNumber,
  formatTime,
  orderStatusLabel,
  orderStatusType,
  paymentStatusLabel,
  paymentStatusType
} from '@/utils/balan'

defineOptions({ name: 'BalanAppUser' })

const { proxy } = getCurrentInstance()
const router = useRouter()
const loading = ref(false)
const showSearch = ref(true)
const userList = ref([])
const total = ref(0)
const queryRef = ref()
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  status: undefined
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const summaryCards = computed(() => {
  const rows = userList.value
  const charging = rows.filter(row => row.activeOrderNo).length
  const paid = rows.reduce((sum, row) => sum + Number(row.paidAmount || 0), 0)
  const refunded = rows.reduce((sum, row) => sum + Number(row.refundedAmount || 0), 0)
  return [
    { label: '用户总数', value: total.value, note: '当前查询范围' },
    { label: '正在充电', value: charging, note: '当前页', tone: 'success' },
    { label: '本页已支付', value: formatMoney(paid), note: '当前页用户累计' },
    { label: '本页已退款', value: formatMoney(refunded), note: '当前页用户累计', tone: 'warning' }
  ]
})

function getList() {
  loading.value = true
  listAppUser(queryParams).then(res => {
    userList.value = res.rows || []
    total.value = res.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryRef.value?.resetFields()
  queryParams.pageNum = 1
  getList()
}

function handleExport() {
  proxy.download('/api/v1/app-users/export', { ...queryParams }, `小程序用户_${new Date().getTime()}.xlsx`)
}

function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  getAppUser(row.id).then(res => {
    detail.value = res.data
  }).finally(() => {
    detailLoading.value = false
  })
}

function toggleStatus(row) {
  const disable = row.status === '0'
  const action = disable ? '停用' : '启用'
  ElMessageBox.confirm(
    disable
      ? `停用后用户「${row.nickname || row.phone || row.id}」已登录的 App 会话会立即失效，确认继续？`
      : `确认启用用户「${row.nickname || row.phone || row.id}」吗？`,
    `${action}账号`,
    { type: disable ? 'warning' : 'info', confirmButtonText: action, cancelButtonText: '取消' }
  ).then(() => {
    return changeAppUserStatus(row.id, disable ? '1' : '0').then(() => {
      ElMessage.success(`已${action}账号`)
      if (detail.value && detail.value.id === row.id) {
        detail.value.status = disable ? '1' : '0'
      }
      getList()
    })
  }).catch(() => {})
}

function openOrder(row) {
  detailVisible.value = false
  router.push({ path: '/order/list', query: { keyword: row.orderNo } })
}

onMounted(getList)
</script>

<style scoped lang="scss">
.appuser-page {
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

    &.success strong {
      color: #16a34a;
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

  .refund-amount {
    color: #d97706;
    font-weight: 600;
  }

  .user-cell {
    display: flex;
    align-items: center;
    gap: 10px;
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

  .detail-body {
    padding: 0 4px 24px;
  }

  .detail-stats {
    margin-bottom: 18px;
  }

  .stat-cell {
    display: flex;
    flex-direction: column;
    gap: 6px;
    height: 100%;
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
      font-size: 20px;
    }

    &.highlight {
      border-color: #bbf7d0;
      background: linear-gradient(180deg, #f0fdf4, #fff);

      strong {
        color: #15803d;
      }
    }

    .active-info {
      font-size: 15px;

      small {
        display: block;
        margin-top: 4px;
        color: #16a34a;
        font-size: 12px;
        font-weight: 400;
      }
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

  .default-tag {
    margin-left: 6px;
  }
}
</style>

<template>
  <div class="app-container work-order-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">OPERATIONS · WORK ORDER</p>
        <h2>运维工单</h2>
        <p class="page-desc">告警一键生成工单，指派到人、记录处理过程，解决后归档闭环。</p>
      </div>
      <div class="page-actions">
        <el-button v-hasPermi="['workorder:add']" type="primary" icon="Plus" @click="openCreate">新建工单</el-button>
        <el-button icon="Refresh" @click="refreshAll">刷新</el-button>
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
          <el-select v-model="queryParams.stationId" placeholder="全部场站" clearable style="width: 180px">
            <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="工单状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option v-for="item in workOrderStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-select v-model="queryParams.priority" placeholder="全部级别" clearable style="width: 120px">
            <el-option v-for="item in workOrderPriorityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源" prop="source">
          <el-select v-model="queryParams.source" placeholder="全部来源" clearable style="width: 130px">
            <el-option v-for="item in workOrderSourceOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="工单号 / 标题 / 设备" clearable style="width: 200px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 350px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="toolbar">
        <el-col :span="1.5">
          <el-tag type="info" effect="plain">共 {{ total }} 个工单</el-tag>
        </el-col>
        <el-col :span="1.5">
          <el-checkbox v-model="queryParams.mine" @change="handleQuery">只看指派给我</el-checkbox>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="refreshAll" />
      </el-row>

      <el-table v-loading="loading" :data="list" row-key="id" @row-dblclick="row => openDetail(row.id)">
        <el-table-column label="工单" min-width="240">
          <template #default="{ row }">
            <div class="strong">{{ row.title }}</div>
            <div class="muted">
              {{ row.workOrderNo }}
              <el-tag size="small" effect="plain" class="source-tag">{{ workOrderSourceLabel(row.source) }}</el-tag>
              <template v-if="row.alertType"> · {{ alertTypeLabel(row.alertType) }}</template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="场站 / 设备" min-width="190">
          <template #default="{ row }">
            <div>{{ row.stationName || '-' }}</div>
            <div class="muted">
              {{ row.deviceNo || '未关联设备' }}<template v-if="row.portNo"> · {{ row.portNo }} 号口</template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="workOrderPriorityType(row.priority)" effect="plain">
              {{ workOrderPriorityLabel(row.priority) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="workOrderStatusType(row.status)" effect="plain">
              {{ workOrderStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" min-width="120">
          <template #default="{ row }">
            <span v-if="row.assigneeName">{{ row.assigneeName }}</span>
            <span v-else class="muted">未指派</span>
          </template>
        </el-table-column>
        <el-table-column label="处理记录" width="150" align="center">
          <template #default="{ row }">
            <div>{{ row.recordCount }} 条</div>
            <div v-if="row.lastRecordAt" class="muted">{{ formatTime(row.lastRecordAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="创建" min-width="170">
          <template #default="{ row }">
            <div>{{ row.creator }}</div>
            <div class="muted">{{ formatTime(row.createTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="openDetail(row.id)">详情</el-button>
            <el-button
              v-if="row.status === 'OPEN' || row.status === 'PROCESSING'"
              v-hasPermi="['workorder:assign']"
              link
              type="primary"
              icon="User"
              @click="openAssign(row)"
            >指派</el-button>
            <el-button
              v-if="row.status === 'OPEN' || row.status === 'PROCESSING'"
              v-hasPermi="['workorder:process']"
              link
              type="warning"
              icon="EditPen"
              @click="handleRecord(row)"
            >记录</el-button>
            <el-button
              v-if="row.status === 'OPEN' || row.status === 'PROCESSING'"
              v-hasPermi="['workorder:process']"
              link
              type="success"
              icon="CircleCheck"
              @click="handleResolve(row)"
            >解决</el-button>
            <el-button
              v-if="row.status === 'RESOLVED'"
              v-hasPermi="['workorder:close']"
              link
              type="info"
              icon="FolderChecked"
              @click="handleClose(row)"
            >归档</el-button>
            <el-button
              v-if="row.status === 'RESOLVED'"
              v-hasPermi="['workorder:process']"
              link
              type="warning"
              icon="RefreshLeft"
              @click="handleReopen(row)"
            >重开</el-button>
            <el-button
              v-if="row.status === 'CLOSED'"
              v-hasPermi="['workorder:remove']"
              link
              type="danger"
              icon="Delete"
              @click="handleDelete(row)"
            >删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无工单，可从告警记录一键生成" />
        </template>
      </el-table>

      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <el-dialog v-model="formVisible" :title="isEdit ? '编辑工单' : '新建工单'" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
        <template v-if="!isEdit">
          <el-form-item label="归属场站" prop="stationId">
            <el-select v-model="form.stationId" placeholder="请选择场站" style="width: 100%" @change="handleStationChange">
              <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="关联设备">
            <el-select
              v-model="form.chargerId"
              placeholder="可选，选择到具体设备"
              clearable
              :loading="chargerLoading"
              :disabled="!form.stationId"
              style="width: 100%"
              @change="handleChargerChange"
            >
              <el-option
                v-for="item in chargerOptions"
                :key="item.id"
                :label="`${item.deviceNo} · ${item.name}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="form.chargerId" label="端口号">
            <el-input-number v-model="form.portNo" :min="1" :max="selectedPortCount" :controls="false" style="width: 120px" />
            <span class="muted form-hint">留空表示整机问题</span>
          </el-form-item>
          <el-form-item label="处理人">
            <el-select v-model="form.assigneeId" placeholder="可暂不指派" clearable filterable style="width: 100%">
              <el-option
                v-for="item in assigneeOptions"
                :key="item.userId"
                :label="`${item.nickName || item.userName}（${item.userName}）`"
                :value="item.userId"
              />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="优先级" prop="priority">
          <el-radio-group v-model="form.priority">
            <el-radio-button v-for="item in workOrderPriorityOptions" :key="item.value" :label="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="工单标题" prop="title">
          <el-input v-model="form.title" maxlength="120" show-word-limit placeholder="例如：3 号口过温告警现场排查" />
        </el-form-item>
        <el-form-item label="问题描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="1000" show-word-limit placeholder="补充现象、影响范围与期望处理方式" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assignVisible" title="指派工单" width="440px" append-to-body>
      <p class="assign-tip">工单 {{ assignTarget?.workOrderNo }} · {{ assignTarget?.title }}</p>
      <el-select v-model="assignForm.assigneeId" placeholder="请选择处理人" filterable style="width: 100%">
        <el-option
          v-for="item in assigneeOptions"
          :key="item.userId"
          :label="`${item.nickName || item.userName}（${item.userName}）`"
          :value="item.userId"
        />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!assignForm.assigneeId" @click="submitAssign">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="46%" :destroy-on-close="true">
      <template #header>
        <div class="detail-header">
          <div>
            <div class="detail-title-line">
              <h3>{{ detail?.workOrder?.title || '工单详情' }}</h3>
              <el-tag v-if="detail" size="small" :type="workOrderStatusType(detail.workOrder.status)" effect="plain">
                {{ workOrderStatusLabel(detail.workOrder.status) }}
              </el-tag>
              <el-tag v-if="detail" size="small" :type="workOrderPriorityType(detail.workOrder.priority)" effect="plain">
                {{ workOrderPriorityLabel(detail.workOrder.priority) }}
              </el-tag>
            </div>
            <p class="muted">
              {{ detail?.workOrder?.workOrderNo }} · {{ detail?.workOrder?.stationName || '-' }}
            </p>
          </div>
          <div v-if="detail" class="detail-actions">
            <el-button
              v-if="detail.workOrder.status === 'OPEN' || detail.workOrder.status === 'PROCESSING'"
              v-hasPermi="['workorder:assign']"
              type="primary"
              plain
              icon="User"
              @click="openAssign(detail.workOrder)"
            >指派</el-button>
            <el-button
              v-if="detail.workOrder.status === 'OPEN' || detail.workOrder.status === 'PROCESSING'"
              v-hasPermi="['workorder:process']"
              type="warning"
              plain
              icon="EditPen"
              @click="handleRecord(detail.workOrder)"
            >记录</el-button>
            <el-button
              v-if="detail.workOrder.status === 'OPEN' || detail.workOrder.status === 'PROCESSING'"
              v-hasPermi="['workorder:process']"
              type="success"
              icon="CircleCheck"
              @click="handleResolve(detail.workOrder)"
            >解决</el-button>
            <el-button
              v-if="detail.workOrder.status === 'RESOLVED'"
              v-hasPermi="['workorder:close']"
              type="info"
              icon="FolderChecked"
              @click="handleClose(detail.workOrder)"
            >归档</el-button>
            <el-button
              v-if="detail.workOrder.status === 'RESOLVED'"
              v-hasPermi="['workorder:process']"
              type="warning"
              plain
              icon="RefreshLeft"
              @click="handleReopen(detail.workOrder)"
            >重开</el-button>
          </div>
        </div>
      </template>

      <div v-loading="detailLoading" class="detail-body">
        <template v-if="detail">
          <div class="section-title">工单信息</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="来源">
              {{ workOrderSourceLabel(detail.workOrder.source) }}
              <template v-if="detail.workOrder.alertType">
                · {{ alertTypeLabel(detail.workOrder.alertType) }}
              </template>
            </el-descriptions-item>
            <el-descriptions-item label="场站">{{ detail.workOrder.stationName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="设备">
              {{ detail.workOrder.deviceNo || '-' }}
              <template v-if="detail.workOrder.portNo"> · {{ detail.workOrder.portNo }} 号口</template>
            </el-descriptions-item>
            <el-descriptions-item label="处理人">{{ detail.workOrder.assigneeName || '未指派' }}</el-descriptions-item>
            <el-descriptions-item label="创建人">{{ detail.workOrder.creator }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatTime(detail.workOrder.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="解决时间">{{ formatTime(detail.workOrder.resolvedAt) }}</el-descriptions-item>
            <el-descriptions-item label="归档时间">
              {{ formatTime(detail.workOrder.closedAt) }}
              <template v-if="detail.workOrder.closedBy"> · {{ detail.workOrder.closedBy }}</template>
            </el-descriptions-item>
          </el-descriptions>

          <div class="section-title">问题描述</div>
          <div class="text-block">{{ detail.workOrder.description || '—' }}</div>

          <template v-if="detail.workOrder.resolution">
            <div class="section-title">处理结论</div>
            <div class="text-block resolution">{{ detail.workOrder.resolution }}</div>
          </template>

          <div class="section-title">处理记录</div>
          <el-timeline v-if="detail.records.length" class="record-timeline">
            <el-timeline-item
              v-for="item in detail.records"
              :key="item.id"
              :timestamp="formatTime(item.createTime)"
              placement="top"
            >
              <div class="record-head">
                <el-tag size="small" effect="plain">{{ workOrderActionLabel(item.action) }}</el-tag>
                <span class="muted">{{ item.operator }}</span>
              </div>
              <div class="record-content">{{ item.content }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else :image-size="60" description="暂无处理记录" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addWorkOrder,
  addWorkOrderRecord,
  assignWorkOrder,
  closeWorkOrder,
  delWorkOrder,
  getWorkOrder,
  getWorkOrderAssignees,
  getWorkOrderStats,
  listWorkOrder,
  reopenWorkOrder,
  resolveWorkOrder,
  updateWorkOrder
} from '@/api/balan/workorder'
import { listCharger } from '@/api/balan/charger'
import { getStationOptions } from '@/api/balan/station'
import useUserStore from '@/store/modules/user'
import {
  alertTypeLabel,
  formatTime,
  workOrderActionLabel,
  workOrderPriorityLabel,
  workOrderPriorityOptions,
  workOrderPriorityType,
  workOrderSourceLabel,
  workOrderSourceOptions,
  workOrderStatusLabel,
  workOrderStatusOptions,
  workOrderStatusType
} from '@/utils/balan'

defineOptions({ name: 'BalanWorkOrder' })

const { proxy } = getCurrentInstance()
const route = useRoute()
const userStore = useUserStore()

const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const list = ref([])
const stats = ref({ total: 0, open: 0, processing: 0, resolved: 0, closed: 0, active: 0 })
const queryRef = ref()
const dateRange = ref([])
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  stationId: undefined,
  status: undefined,
  priority: undefined,
  source: undefined,
  keyword: undefined,
  mine: false,
  beginTime: undefined,
  endTime: undefined
})

const stationOptions = ref([])
const chargerOptions = ref([])
const chargerLoading = ref(false)
const assigneeOptions = ref([])
const canAssign = computed(() => {
  const permissions = userStore.permissions || []
  return permissions.includes('*:*:*') || permissions.includes('workorder:assign')
})

const formVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const formRef = ref()
const form = reactive({
  id: undefined,
  stationId: undefined,
  chargerId: undefined,
  portNo: undefined,
  priority: 'NORMAL',
  assigneeId: undefined,
  title: undefined,
  description: undefined
})
const rules = {
  stationId: [{ required: true, message: '请选择归属场站', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  title: [{ required: true, message: '请填写工单标题', trigger: 'blur' }]
}
const selectedPortCount = computed(() => {
  const charger = chargerOptions.value.find(item => item.id === form.chargerId)
  return charger?.portCount || 12
})

const assignVisible = ref(false)
const assignTarget = ref(null)
const assignForm = reactive({ assigneeId: undefined })

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const summaryCards = computed(() => [
  { label: '待处理', value: stats.value.open ?? 0, note: '尚未开始处理', tone: 'warning' },
  { label: '处理中', value: stats.value.processing ?? 0, note: '已指派或已有记录', tone: 'primary' },
  { label: '待归档', value: stats.value.resolved ?? 0, note: '已解决等待归档', tone: 'success' },
  { label: '已归档', value: stats.value.closed ?? 0, note: '全部工单 ' + (stats.value.total ?? 0) + ' 个' }
])

function getList() {
  loading.value = true
  const params = {
    ...queryParams,
    mine: queryParams.mine || undefined,
    beginTime: dateRange.value?.[0],
    endTime: dateRange.value?.[1]
  }
  return listWorkOrder(params)
    .then(res => {
      list.value = res.rows || []
      total.value = res.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function getStats() {
  return getWorkOrderStats().then(res => {
    stats.value = res.data || {}
  })
}

function refreshAll() {
  return Promise.all([getList(), getStats()])
}

function handleQuery() {
  queryParams.pageNum = 1
  refreshAll()
}

function resetQuery() {
  queryRef.value?.resetFields()
  dateRange.value = []
  queryParams.stationId = undefined
  queryParams.status = undefined
  queryParams.priority = undefined
  queryParams.source = undefined
  queryParams.keyword = undefined
  queryParams.mine = false
  queryParams.pageNum = 1
  refreshAll()
}

function loadAssignees() {
  if (!canAssign.value) {
    return
  }
  getWorkOrderAssignees().then(res => {
    assigneeOptions.value = res.data || []
  })
}

function loadChargers(stationId) {
  chargerOptions.value = []
  if (!stationId) {
    return Promise.resolve()
  }
  chargerLoading.value = true
  return listCharger({ stationId, pageNum: 1, pageSize: 100 })
    .then(res => {
      chargerOptions.value = res.rows || []
    })
    .finally(() => {
      chargerLoading.value = false
    })
}

function openCreate() {
  isEdit.value = false
  resetForm()
  loadAssignees()
  formVisible.value = true
}

function resetForm() {
  form.id = undefined
  form.stationId = undefined
  form.chargerId = undefined
  form.portNo = undefined
  form.priority = 'NORMAL'
  form.assigneeId = undefined
  form.title = undefined
  form.description = undefined
  chargerOptions.value = []
  formRef.value?.clearValidate()
}

function handleStationChange(stationId) {
  form.chargerId = undefined
  form.portNo = undefined
  loadChargers(stationId)
}

function handleChargerChange() {
  form.portNo = undefined
}

function openEdit(row) {
  isEdit.value = true
  form.id = row.id
  form.priority = row.priority || 'NORMAL'
  form.title = row.title
  form.description = row.description
  formRef.value?.clearValidate()
  formVisible.value = true
}

function submitForm() {
  formRef.value.validate(valid => {
    if (!valid) return
    submitting.value = true
    const done = () => {
      submitting.value = false
      formVisible.value = false
      ElMessage.success(isEdit.value ? '工单已更新' : '工单已创建')
      refreshAll()
    }
    const fail = () => {
      submitting.value = false
    }
    if (isEdit.value) {
      updateWorkOrder(form.id, {
        title: form.title,
        description: form.description || null,
        priority: form.priority
      }).then(done).catch(fail)
      return
    }
    addWorkOrder({
      stationId: form.stationId,
      chargerId: form.chargerId || undefined,
      portNo: form.portNo || undefined,
      priority: form.priority,
      assigneeId: form.assigneeId || undefined,
      title: form.title,
      description: form.description
    }).then(done).catch(fail)
  })
}

function openAssign(row) {
  if (!canAssign.value) {
    return
  }
  assignTarget.value = row
  assignForm.assigneeId = row.assigneeId || undefined
  loadAssignees()
  assignVisible.value = true
}

function submitAssign() {
  if (!assignForm.assigneeId) {
    return
  }
  submitting.value = true
  assignWorkOrder(assignTarget.value.id, assignForm.assigneeId)
    .then(res => {
      assignVisible.value = false
      ElMessage.success('已指派')
      syncDetail(res.data)
      refreshAll()
    })
    .finally(() => {
      submitting.value = false
    })
}

async function handleRecord(row) {
  try {
    const { value } = await ElMessageBox.prompt('请填写本次处理内容，将追加到工单时间线。', '处理记录', {
      inputType: 'textarea',
      confirmButtonText: '提交记录',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：已到达现场，检查端口接线与散热风扇',
      inputValidator: input => (input && input.trim() ? true : '请填写处理记录')
    })
    const res = await addWorkOrderRecord(row.id, value.trim())
    ElMessage.success('处理记录已添加')
    syncDetail(res.data)
    refreshAll()
  } catch (error) {
    // 取消不提示
  }
}

async function handleResolve(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      row.alertId ? '填写处理结论。保存后关联告警会同步标记恢复。' : '填写处理结论。',
      '标记解决',
      {
        inputType: 'textarea',
        confirmButtonText: '标记解决',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：更换散热风扇后复测 30 分钟温度正常',
        inputValidator: input => (input && input.trim() ? true : '请填写处理结论')
      }
    )
    const res = await resolveWorkOrder(row.id, value.trim())
    ElMessage.success('工单已解决')
    syncDetail(res.data)
    refreshAll()
  } catch (error) {
    // 取消不提示
  }
}

function handleClose(row) {
  ElMessageBox.confirm('归档后工单进入只读状态，确认归档？', '归档工单', {
    type: 'info',
    confirmButtonText: '归档',
    cancelButtonText: '取消'
  })
    .then(() => closeWorkOrder(row.id))
    .then(res => {
      ElMessage.success('工单已归档')
      syncDetail(res.data)
      refreshAll()
    })
    .catch(() => {})
}

function handleReopen(row) {
  ElMessageBox.confirm('重新打开后工单回到处理中，确认继续处理？', '重新打开工单', {
    type: 'warning',
    confirmButtonText: '重新打开',
    cancelButtonText: '取消'
  })
    .then(() => reopenWorkOrder(row.id))
    .then(res => {
      ElMessage.success('工单已重新打开')
      syncDetail(res.data)
      refreshAll()
    })
    .catch(() => {})
}

function handleDelete(row) {
  ElMessageBox.confirm('删除后不可恢复，确认删除已归档工单？', '删除工单', { type: 'warning' })
    .then(() => delWorkOrder(row.id))
    .then(() => {
      ElMessage.success('工单已删除')
      if (detail.value?.workOrder?.id === row.id) {
        detailVisible.value = false
      }
      refreshAll()
    })
    .catch(() => {})
}

function openDetail(id) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  getWorkOrder(id)
    .then(res => {
      detail.value = res.data
    })
    .finally(() => {
      detailLoading.value = false
    })
}

function syncDetail(updated) {
  if (!updated || !detail.value || detail.value.workOrder?.id !== updated.id) {
    return
  }
  getWorkOrder(updated.id).then(res => {
    detail.value = res.data
  })
}

onMounted(() => {
  getStationOptions().then(res => {
    stationOptions.value = res.data || []
  })
  loadAssignees()
  refreshAll()
  const routeId = Number(route.query.id)
  if (routeId) {
    openDetail(routeId)
  }
})
</script>

<style scoped lang="scss">
.work-order-page {
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

    &.primary strong {
      color: #2563eb;
    }

    &.success strong {
      color: #16a34a;
    }
  }

  .table-card {
    border: 1px solid #e2e8f0;
    border-radius: 12px;
  }

  .toolbar {
    display: flex;
    align-items: center;
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

  .source-tag {
    margin: 0 4px;
  }

  .form-hint {
    margin-left: 8px;
  }

  .assign-tip {
    margin: 0 0 14px;
    color: #64748b;
  }

  .detail-header {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
    width: 100%;
  }

  .detail-title-line {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;

    h3 {
      margin: 0;
      color: #0f172a;
      font-size: 18px;
      line-height: 1.4;
    }
  }

  .detail-header p {
    margin: 6px 0 0;
  }

  .detail-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    justify-content: flex-start;
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

  .text-block {
    padding: 12px 14px;
    border-radius: 8px;
    background: #f8fafc;
    color: #334155;
    line-height: 1.7;
    white-space: pre-wrap;
  }

  .text-block.resolution {
    background: #ecfdf5;
    color: #166534;
  }

  .record-timeline {
    padding-left: 2px;
  }

  .record-head {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .record-content {
    margin-top: 6px;
    color: #334155;
    line-height: 1.6;
    white-space: pre-wrap;
  }
}
</style>

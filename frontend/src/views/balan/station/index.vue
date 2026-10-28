<template>
  <div class="app-container station-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">OPERATIONS · STATION</p>
        <h2>场站管理</h2>
        <p class="page-desc">维护场站档案与运营状态，账号只能看到自己获授权的场站。</p>
      </div>
      <div class="page-actions">
        <el-button icon="Refresh" @click="getList">刷新</el-button>
        <el-button v-hasPermi="['station:add']" type="primary" icon="Plus" @click="handleAdd">新增场站</el-button>
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
      <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="72px">
        <el-form-item label="场站名称" prop="name">
          <el-input
            v-model="queryParams.name"
            placeholder="请输入场站名称"
            clearable
            style="width: 220px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 160px">
            <el-option label="运营中" value="ACTIVE" />
            <el-option label="已停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="toolbar">
        <el-col :span="1.5">
          <el-tag type="info" effect="plain">共 {{ total }} 个场站</el-tag>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="stationList" row-key="id">
        <el-table-column label="场站" min-width="240">
          <template #default="{ row }">
            <div class="strong">{{ row.name }}</div>
            <div class="muted">{{ row.address || '未填写地址' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="容量" width="110">
          <template #default="{ row }">{{ formatNumber(row.capacityKw, 2) }} kW</template>
        </el-table-column>
        <el-table-column label="负责人" width="170">
          <template #default="{ row }">
            <div>{{ row.manager || '-' }}</div>
            <div class="muted">{{ row.phone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="设备" width="120">
          <template #default="{ row }">
            <span class="online-count">{{ row.onlineDeviceCount || 0 }}</span>
            <span class="muted"> / {{ row.deviceCount || 0 }} 在线</span>
          </template>
        </el-table-column>
        <el-table-column label="端口" width="140">
          <template #default="{ row }">
            <span class="charging-count">{{ row.chargingPortCount || 0 }}</span>
            <span class="muted"> / {{ row.portCount || 0 }} 充电中</span>
          </template>
        </el-table-column>
        <el-table-column label="故障端口" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.faultPortCount > 0 ? 'danger' : 'info'" effect="plain" size="small">
              {{ row.faultPortCount || 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusOf(row.status).type" effect="plain" size="small">{{ statusOf(row.status).label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="View" @click="handleDetail(row)">详情</el-button>
            <el-button v-hasPermi="['station:edit']" link type="primary" icon="Edit" @click="handleUpdate(row)">编辑</el-button>
            <el-button v-hasPermi="['station:remove']" link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑场站' : '新增场站'" width="640px" append-to-body @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="场站名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入场站名称" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="场站地址" prop="address">
              <el-input v-model="form.address" placeholder="请输入详细地址" maxlength="255" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="纬度" prop="latitude">
              <el-input-number
                v-model="form.latitude"
                :min="-90"
                :max="90"
                :precision="7"
                :controls="false"
                placeholder="如 31.2304000"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="经度" prop="longitude">
              <el-input-number
                v-model="form.longitude"
                :min="-180"
                :max="180"
                :precision="7"
                :controls="false"
                placeholder="如 121.4737000"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="额定容量" prop="capacityKw">
              <el-input-number v-model="form.capacityKw" :min="0" :precision="2" :step="10" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio label="ACTIVE">运营中</el-radio>
                <el-radio label="DISABLED">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="负责人" prop="manager">
              <el-input v-model="form.manager" placeholder="请输入负责人" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="补充说明" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" title="场站详情" size="58%" @closed="detail = null">
      <div v-if="detail" v-loading="detailLoading" class="detail-body">
        <div class="detail-title">
          <div>
            <h3>{{ detail.name }}</h3>
            <p class="muted">{{ detail.address || '未填写地址' }}</p>
          </div>
          <el-tag :type="statusOf(detail.status).type" effect="plain">{{ statusOf(detail.status).label }}</el-tag>
        </div>

        <el-descriptions :column="3" border class="detail-desc">
          <el-descriptions-item label="额定容量">{{ formatNumber(detail.capacityKw, 2) }} kW</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ detail.manager || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detail.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="设备总数">{{ detail.deviceCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="在线设备">{{ detail.onlineDeviceCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="端口总数">{{ detail.portCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="充电中端口">{{ detail.chargingPortCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="故障端口">{{ detail.faultPortCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="坐标">
            <span v-if="detail.latitude != null && detail.longitude != null">
              {{ Number(detail.latitude).toFixed(6) }}, {{ Number(detail.longitude).toFixed(6) }}
            </span>
            <span v-else class="muted">未填写</span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{ detail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">
          <span>场站设备</span>
          <el-tag size="small" type="info" effect="plain">{{ stationChargers.length }} 台</el-tag>
        </div>
        <el-table v-loading="chargerLoading" :data="stationChargers" size="small">
          <el-table-column prop="deviceNo" label="设备编号" width="150" />
          <el-table-column prop="name" label="设备名称" min-width="140" />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">{{ deviceTypeLabel(row.deviceType) }}</template>
          </el-table-column>
          <el-table-column label="在线" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.onlineStatus === 'ONLINE' ? 'success' : 'info'" size="small" effect="plain">
                {{ row.onlineStatus === 'ONLINE' ? '在线' : '离线' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="端口" width="150">
            <template #default="{ row }">
              <span class="charging-count">{{ row.chargingPorts || 0 }}</span> 充 /
              <span>{{ row.idlePorts || 0 }}</span> 闲 /
              <span class="fault-count">{{ row.faultPorts || 0 }}</span> 障
            </template>
          </el-table-column>
          <el-table-column label="实时功率" width="110">
            <template #default="{ row }">{{ formatNumber(row.currentPowerKw, 2) }} kW</template>
          </el-table-column>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { addStation, delStation, getStation, listStation, updateStation } from '@/api/balan/station'
import { listCharger } from '@/api/balan/charger'
import { deviceTypeLabel, formatNumber, formatTime } from '@/utils/balan'

defineOptions({ name: 'BalanStation' })

const loading = ref(false)
const showSearch = ref(true)
const stationList = ref([])
const total = ref(0)
const queryRef = ref()
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  name: undefined,
  status: undefined
})

const summaryCards = computed(() => [
  { label: '场站总数', value: total.value, note: '当前查询范围' },
  { label: '设备总数', value: sumOf('deviceCount'), note: '当前页设备' },
  { label: '在线设备', value: sumOf('onlineDeviceCount'), note: 'MQTT 心跳正常' },
  { label: '充电中端口', value: sumOf('chargingPortCount'), note: `共 ${sumOf('portCount')} 个端口` }
])

function sumOf(field) {
  return stationList.value.reduce((sum, row) => sum + (row[field] || 0), 0)
}

const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref()
const form = ref(defaultForm())
const rules = {
  name: [{ required: true, message: '场站名称不能为空', trigger: 'blur' }],
  capacityKw: [{ required: true, message: '请输入额定容量', trigger: 'blur' }],
  phone: [{ pattern: /^[0-9+\-\s]{0,30}$/, message: '联系电话格式不正确', trigger: 'blur' }]
}

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const stationChargers = ref([])
const chargerLoading = ref(false)

function defaultForm() {
  return {
    id: null,
    name: '',
    address: '',
    latitude: null,
    longitude: null,
    capacityKw: 0,
    manager: '',
    phone: '',
    status: 'ACTIVE',
    remark: ''
  }
}

function statusOf(status) {
  return status === 'DISABLED'
    ? { label: '已停用', type: 'info' }
    : { label: '运营中', type: 'success' }
}

function getList() {
  loading.value = true
  listStation(queryParams).then(res => {
    stationList.value = res.rows || []
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

function handleAdd() {
  form.value = defaultForm()
  dialogVisible.value = true
}

function handleUpdate(row) {
  form.value = {
    id: row.id,
    name: row.name,
    address: row.address || '',
    latitude: row.latitude != null ? Number(row.latitude) : null,
    longitude: row.longitude != null ? Number(row.longitude) : null,
    capacityKw: row.capacityKw ? Number(row.capacityKw) : 0,
    manager: row.manager || '',
    phone: row.phone || '',
    status: row.status || 'ACTIVE',
    remark: row.remark || ''
  }
  dialogVisible.value = true
}

function submitForm() {
  formRef.value.validate(valid => {
    if (!valid) {
      return
    }
    submitLoading.value = true
    const payload = {
      name: form.value.name,
      address: form.value.address,
      latitude: form.value.latitude,
      longitude: form.value.longitude,
      capacityKw: form.value.capacityKw,
      manager: form.value.manager,
      phone: form.value.phone,
      status: form.value.status,
      remark: form.value.remark
    }
    const request = form.value.id ? updateStation(form.value.id, payload) : addStation(payload)
    request.then(() => {
      ElMessage.success(form.value.id ? '场站已更新' : '场站已创建')
      dialogVisible.value = false
      getList()
    }).finally(() => {
      submitLoading.value = false
    })
  })
}

function resetForm() {
  form.value = defaultForm()
  formRef.value?.resetFields()
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除场站「${row.name}」吗？删除前需先移除其下全部设备。`, '删除提示', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(() => delStation(row.id)).then(() => {
    ElMessage.success('场站已删除')
    getList()
    if (detail.value?.id === row.id) {
      detailVisible.value = false
    }
  }).catch(() => {})
}

function handleDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  stationChargers.value = []
  getStation(row.id).then(res => {
    detail.value = res.data
  }).finally(() => {
    detailLoading.value = false
  })
  chargerLoading.value = true
  listCharger({ stationId: row.id, pageNum: 1, pageSize: 100 }).then(res => {
    stationChargers.value = res.rows || []
  }).finally(() => {
    chargerLoading.value = false
  })
}

onMounted(getList)
</script>

<style scoped lang="scss">
.station-page {
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
      font-size: 26px;
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

  .online-count {
    color: #16a34a;
    font-weight: 600;
  }

  .charging-count {
    color: #0ea5e9;
    font-weight: 600;
  }

  .fault-count {
    color: #dc2626;
    font-weight: 600;
  }

  .detail-body {
    padding: 0 4px 24px;
  }

  .detail-title {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    margin-bottom: 16px;

    h3 {
      margin: 0 0 6px;
      color: #0f172a;
    }
  }

  .detail-desc {
    margin-bottom: 20px;
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
    color: #0f172a;
    font-weight: 600;
  }
}
</style>

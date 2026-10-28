<template>
  <div class="app-container vehicle-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">OPERATIONS · VEHICLE</p>
        <h2>车辆管理</h2>
        <p class="page-desc">维护小程序用户绑定的车辆信息；订单启动时可选车，充电记录会带上车牌。</p>
      </div>
      <div class="page-actions">
        <el-button icon="Refresh" @click="getList">刷新</el-button>
        <el-button v-hasPermi="['vehicle:list']" type="primary" plain icon="Download" @click="handleExport">导出 Excel</el-button>
        <el-button v-hasPermi="['vehicle:add']" type="primary" icon="Plus" @click="openAdd">新增车辆</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="76px">
        <el-form-item label="归属用户" prop="appUserId">
          <el-select
            v-model="queryParams.appUserId"
            placeholder="全部用户"
            clearable
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userLoading"
            style="width: 220px"
          >
            <el-option v-for="item in userOptions" :key="item.id" :label="userLabel(item)" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="车牌 / 品牌 / 车型" clearable style="width: 220px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="toolbar">
        <el-col :span="1.5">
          <el-tag type="info" effect="plain">共 {{ total }} 辆</el-tag>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="vehicleList" row-key="id" empty-text="暂无车辆">
        <el-table-column label="车牌号" min-width="150">
          <template #default="{ row }">
            <span class="strong plate">{{ row.plateNo }}</span>
            <el-tag v-if="row.isDefault === '1'" size="small" type="success" effect="plain" class="default-tag">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="VIN" min-width="190">
          <template #default="{ row }">
            <span class="vin">{{ row.vin || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="归属用户" min-width="180">
          <template #default="{ row }">
            <div>{{ row.appUserNickname || '小程序用户' }}</div>
            <div class="muted">{{ row.appUserPhone || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="品牌 / 车型" min-width="180">
          <template #default="{ row }">{{ [row.brand, row.model].filter(Boolean).join(' ') || '-' }}</template>
        </el-table-column>
        <el-table-column label="颜色" width="100">
          <template #default="{ row }">{{ row.color || '-' }}</template>
        </el-table-column>
        <el-table-column label="绑定时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-hasPermi="['vehicle:edit']" link type="primary" icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button v-hasPermi="['vehicle:remove']" link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑车辆' : '新增车辆'" width="480px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="归属用户" prop="appUserId">
          <el-select
            v-model="form.appUserId"
            placeholder="请选择小程序用户"
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userLoading"
            :disabled="!!form.id"
            style="width: 100%"
          >
            <el-option v-for="item in userOptions" :key="item.id" :label="userLabel(item)" :value="item.id" />
          </el-select>
          <div v-if="form.id" class="form-tip">车辆归属用户不可修改，如需变更请删除后重新绑定。</div>
        </el-form-item>
        <el-form-item label="车牌号" prop="plateNo">
          <el-input v-model="form.plateNo" maxlength="16" placeholder="例如 粤B12345" />
        </el-form-item>
        <el-form-item label="VIN" prop="vin">
          <el-input
            v-model="form.vin"
            maxlength="17"
            placeholder="选填，17 位车架号"
            @input="form.vin = (form.vin || '').toUpperCase()"
          />
          <div class="form-tip">插枪即充的交流桩按 VIN 反查车主建单，不填则该车无法即插即充。</div>
        </el-form-item>
        <el-form-item label="品牌">
          <el-input v-model="form.brand" maxlength="32" placeholder="例如 比亚迪" />
        </el-form-item>
        <el-form-item label="车型">
          <el-input v-model="form.model" maxlength="64" placeholder="例如 秦 PLUS" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-input v-model="form.color" maxlength="16" placeholder="例如 白色" />
        </el-form-item>
        <el-form-item label="默认车辆">
          <el-switch v-model="form.isDefault" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { getCurrentInstance, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listAppUser } from '@/api/balan/appuser'
import { addVehicle, deleteVehicle, getVehicle, listVehicle, updateVehicle } from '@/api/balan/vehicle'
import { formatTime } from '@/utils/balan'

defineOptions({ name: 'BalanVehicle' })

const { proxy } = getCurrentInstance()
const loading = ref(false)
const showSearch = ref(true)
const vehicleList = ref([])
const total = ref(0)
const queryRef = ref()
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  appUserId: undefined,
  keyword: undefined
})

const userOptions = ref([])
const userLoading = ref(false)

const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref()
const form = reactive({
  id: undefined,
  appUserId: undefined,
  plateNo: '',
  vin: '',
  brand: '',
  model: '',
  color: '',
  isDefault: false
})
const rules = {
  appUserId: [{ required: true, message: '请选择归属用户', trigger: 'change' }],
  plateNo: [{ required: true, message: '请输入车牌号', trigger: 'blur' }],
  vin: [{ pattern: /^[A-HJ-NPR-Z0-9]{17}$/, message: 'VIN 必须是 17 位，且不含 I / O / Q', trigger: 'blur' }]
}

function getList() {
  loading.value = true
  listVehicle(queryParams).then(res => {
    vehicleList.value = res.rows || []
    total.value = res.total || 0
  }).finally(() => {
    loading.value = false
  })
}

function searchUsers(keyword) {
  userLoading.value = true
  listAppUser({ pageNum: 1, pageSize: 20, keyword: keyword || undefined }).then(res => {
    const rows = res.rows || []
    const current = userOptions.value.filter(item => !rows.some(row => row.id === item.id))
    userOptions.value = mergeUsers(rows, keyword ? [] : current)
  }).finally(() => {
    userLoading.value = false
  })
}

function mergeUsers(primary, extra) {
  const map = new Map()
  ;[...primary, ...extra].forEach(item => {
    if (item && item.id) {
      map.set(item.id, item)
    }
  })
  return Array.from(map.values())
}

function ensureUserOption(user) {
  if (user && user.id && !userOptions.value.some(item => item.id === user.id)) {
    userOptions.value = [user, ...userOptions.value]
  }
}

function userLabel(user) {
  if (!user) {
    return '-'
  }
  const name = user.nickname || user.appUserNickname || '小程序用户'
  const phone = user.phone || user.appUserPhone
  return phone ? `${name} · ${phone}` : name
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
  proxy.download('/api/v1/vehicles/export', { ...queryParams }, `车辆管理_${new Date().getTime()}.xlsx`)
}

function resetForm() {
  form.id = undefined
  form.appUserId = undefined
  form.plateNo = ''
  form.vin = ''
  form.brand = ''
  form.model = ''
  form.color = ''
  form.isDefault = false
  formRef.value?.clearValidate()
}

function openAdd() {
  resetForm()
  searchUsers('')
  dialogVisible.value = true
}

function openEdit(row) {
  getVehicle(row.id).then(res => {
    const detail = res.data || row
    resetForm()
    ensureUserOption({ id: detail.appUserId, nickname: detail.appUserNickname, phone: detail.appUserPhone })
    Object.assign(form, {
      id: detail.id,
      appUserId: detail.appUserId,
      plateNo: detail.plateNo,
      vin: detail.vin || '',
      brand: detail.brand || '',
      model: detail.model || '',
      color: detail.color || '',
      isDefault: detail.isDefault === '1'
    })
    dialogVisible.value = true
  }).catch(() => {})
}

function submitForm() {
  formRef.value?.validate(valid => {
    if (!valid) {
      return
    }
    submitLoading.value = true
    const payload = {
      appUserId: form.appUserId,
      plateNo: form.plateNo,
      vin: form.vin || undefined,
      brand: form.brand,
      model: form.model,
      color: form.color,
      isDefault: form.isDefault
    }
    const request = form.id ? updateVehicle(form.id, payload) : addVehicle(payload)
    request.then(() => {
      ElMessage.success(form.id ? '车辆已更新' : '车辆已新增')
      dialogVisible.value = false
      getList()
    }).finally(() => {
      submitLoading.value = false
    })
  })
}

function handleDelete(row) {
  ElMessageBox.confirm(`确认删除车辆「${row.plateNo}」吗？删除后不影响历史订单。`, '删除车辆', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(() => {
    return deleteVehicle(row.id).then(() => {
      ElMessage.success('车辆已删除')
      getList()
    })
  }).catch(() => {})
}

onMounted(() => {
  searchUsers('')
  getList()
})
</script>

<style scoped lang="scss">
.vehicle-page {
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

  .plate {
    font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
  }

  .vin {
    color: #334155;
    font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
    font-size: 12px;
  }

  .default-tag {
    margin-left: 6px;
  }

  .form-tip {
    margin-top: 4px;
    color: #94a3b8;
    font-size: 12px;
    line-height: 1.5;
  }
}
</style>

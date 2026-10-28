<template>
  <div class="app-container rate-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">OPERATIONS · RATE</p>
        <h2>费率策略</h2>
        <p class="page-desc">电价策略与服务费策略独立配置，可按生效日期区间自动切换；启用新策略时，同场站同类型且日期重叠的旧策略会自动停用。</p>
      </div>
      <div class="page-actions">
        <el-button icon="Refresh" @click="getList">刷新</el-button>
        <el-button v-hasPermi="['rate:add']" type="primary" icon="Plus" @click="handleAdd">新增策略</el-button>
      </div>
    </div>

    <el-row :gutter="14" class="summary-row">
      <el-col v-for="item in summaryCards" :key="item.label" :xs="12" :sm="8">
        <el-card shadow="never" class="summary-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <small>{{ item.note }}</small>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card">
      <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="72px">
        <el-form-item label="归属场站" prop="stationId">
          <el-select v-model="queryParams.stationId" placeholder="全部场站" clearable style="width: 200px">
            <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="策略类型" prop="strategyType">
          <el-select v-model="queryParams.strategyType" placeholder="全部类型" clearable style="width: 150px">
            <el-option v-for="item in strategyTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="策略名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="启用状态" prop="enabled">
          <el-select v-model="queryParams.enabled" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="已启用" :value="1" />
            <el-option label="未启用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="toolbar">
        <el-col :span="1.5">
          <el-tag type="info" effect="plain">共 {{ total }} 套策略</el-tag>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="rateList" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="period-panel">
              <el-table :data="row.periods" size="small" border>
                <el-table-column label="时段类型" width="100" align="center">
                  <template #default="{ row: period }">
                    <el-tag size="small" :type="periodTagType(period.periodType)" effect="plain">
                      {{ periodTypeLabel(period.periodType) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="时间窗" width="170">
                  <template #default="{ row: period }">{{ periodWindow(period) }}</template>
                </el-table-column>
                <el-table-column v-if="row.strategyType !== 'SERVICE'" label="时间费 (元/小时)" min-width="130" align="right">
                  <template #default="{ row: period }">{{ formatNumber(period.timePrice, 4) }}</template>
                </el-table-column>
                <el-table-column v-if="row.strategyType !== 'SERVICE'" label="电费 (元/kWh)" min-width="130" align="right">
                  <template #default="{ row: period }">{{ formatNumber(period.energyPrice, 4) }}</template>
                </el-table-column>
                <el-table-column label="服务费" min-width="120" align="right">
                  <template #default="{ row: period }">{{ formatNumber(period.servicePrice, 4) }}</template>
                </el-table-column>
                <el-table-column label="服务费单位" width="130" align="center">
                  <template #default="{ row: period }">{{ period.serviceUnit === 'HOUR' ? '元/小时' : '元/kWh' }}</template>
                </el-table-column>
              </el-table>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="策略名称" min-width="200">
          <template #default="{ row }">
            <div class="name-line">
              <span class="strong">{{ row.name }}</span>
              <el-tag size="small" :type="row.strategyType === 'SERVICE' ? 'warning' : 'primary'" effect="plain">
                {{ strategyTypeLabel(row.strategyType) }}
              </el-tag>
              <el-tag v-if="row.current" size="small" type="success" effect="dark">生效中</el-tag>
              <el-tag v-else-if="row.enabled === 1" size="small" type="info" effect="plain">已启用 · 未到生效期</el-tag>
            </div>
            <div class="muted">{{ row.remark || '暂无备注' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="stationName" label="归属场站" min-width="150" show-overflow-tooltip />
        <el-table-column label="计费模式" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.strategyType === 'SERVICE'" class="muted">仅服务费</span>
            <el-tag v-else size="small" effect="plain">{{ billingModeLabel(row.billingMode) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效日期" width="200">
          <template #default="{ row }">
            <span class="muted">{{ effectiveRangeLabel(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时段配置" min-width="260">
          <template #default="{ row }">
            <div class="period-tags">
              <el-tag
                v-for="period in row.periods.slice(0, 4)"
                :key="period.id || period.periodType + period.startTime"
                size="small"
                :type="periodTagType(period.periodType)"
                effect="plain"
              >
                {{ periodTypeLabel(period.periodType) }} {{ periodWindow(period) }}
              </el-tag>
              <span v-if="row.periods.length > 4" class="muted">+{{ row.periods.length - 4 }}</span>
              <span v-if="row.periods.length === 0" class="muted">未配置</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              v-hasPermi="['rate:enable']"
              :model-value="row.enabled === 1"
              :loading="row.switching"
              @change="value => toggleEnabled(row, value)"
            />
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">
            <span class="muted">{{ formatTime(row.updateTime || row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-hasPermi="['rate:edit']" link type="primary" icon="Edit" @click="handleUpdate(row)">编辑</el-button>
            <el-button v-hasPermi="['rate:remove']" link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog
      v-model="dialogVisible"
      class="rate-dialog"
      :title="form.id ? '编辑费率策略' : (form.strategyType === 'SERVICE' ? '新增服务费策略' : '新增电价策略')"
      width="min(980px, 94vw)"
      top="5vh"
      append-to-body
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="98px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="策略类型" prop="strategyType">
              <el-radio-group v-model="form.strategyType" @change="handleStrategyTypeChange">
                <el-radio-button label="ENERGY">电价策略</el-radio-button>
                <el-radio-button label="SERVICE">服务费策略</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生效日期">
              <el-date-picker
                v-model="form.effectiveRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期（不限）"
                end-placeholder="结束日期（不限）"
                range-separator="至"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="10">
            <el-form-item label="归属场站" prop="stationId">
              <el-select v-model="form.stationId" placeholder="请选择场站" style="width: 100%">
                <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="14">
            <el-form-item label="策略名称" prop="name">
              <el-input v-model="form.name" :placeholder="form.strategyType === 'SERVICE' ? '如 2026 年服务费' : '如 峰平谷分时电价'" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.strategyType !== 'SERVICE'" :span="10">
            <el-form-item label="计费模式" prop="billingMode">
              <el-radio-group v-model="form.billingMode">
                <el-radio-button v-for="item in billingModeOptions" :key="item.value" :label="item.value">
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="14">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" placeholder="可选" maxlength="500" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="period-editor-head">
          <div>
            <span class="panel-title">时段与{{ form.strategyType === 'SERVICE' ? '服务费' : '价格' }}</span>
            <span class="muted">
              {{ form.strategyType === 'SERVICE'
                ? '需完整覆盖 00:00-24:00；仅服务费参与账单，时间费与电费由电价策略决定。'
                : '需完整覆盖 00:00-24:00；同一时段类型可拆多个时间窗，但价格必须一致。' }}
            </span>
          </div>
          <div class="period-actions">
            <el-button size="small" @click="applyPreset">填入{{ form.strategyType === 'SERVICE' ? '服务费' : '峰平谷' }}模板</el-button>
            <el-button size="small" type="primary" plain icon="Plus" @click="addPeriod">新增时段</el-button>
          </div>
        </div>

        <el-table :data="form.periods" border size="small" class="period-edit-table">
          <el-table-column label="时段类型" width="110" align="center">
            <template #default="{ row }">
              <el-select v-model="row.periodType" size="small" style="width: 82px">
                <el-option v-for="item in periodTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="开始" width="125" align="center">
            <template #default="{ row }">
              <el-time-picker
                v-model="row.startTime"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="开始"
                :clearable="false"
                size="small"
                style="width: 100px"
              />
            </template>
          </el-table-column>
          <el-table-column label="结束" width="125" align="center">
            <template #default="{ row }">
              <el-time-picker
                v-model="row.endTime"
                format="HH:mm"
                value-format="HH:mm"
                placeholder="结束"
                :clearable="false"
                size="small"
                style="width: 100px"
              />
            </template>
          </el-table-column>
          <el-table-column v-if="form.strategyType !== 'SERVICE'" label="时间费 (元/小时)" min-width="125" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.timePrice" :min="0" :max="1000" :precision="4" :step="0.1" size="small" controls-position="right" style="width: 110px" />
            </template>
          </el-table-column>
          <el-table-column v-if="form.strategyType !== 'SERVICE'" label="电费 (元/kWh)" min-width="125" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.energyPrice" :min="0" :max="1000" :precision="4" :step="0.1" size="small" controls-position="right" style="width: 110px" />
            </template>
          </el-table-column>
          <el-table-column label="服务费" min-width="115" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.servicePrice" :min="0" :max="1000" :precision="4" :step="0.05" size="small" controls-position="right" style="width: 100px" />
            </template>
          </el-table-column>
          <el-table-column label="服务费单位" width="125" align="center">
            <template #default="{ row }">
              <el-select v-model="row.serviceUnit" size="small" style="width: 100px">
                <el-option label="元/kWh" value="KWH" />
                <el-option label="元/小时" value="HOUR" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" icon="Delete" @click="removePeriod($index)" />
            </template>
          </el-table-column>
        </el-table>

        <div class="billing-hint">
          <el-tag size="small" effect="plain">{{ form.strategyType === 'SERVICE' ? '服务费策略' : billingModeLabel(form.billingMode) }}</el-tag>
          <span>{{ billingHint }}</span>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">保存策略</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addRate,
  delRate,
  disableRate,
  enableRate,
  getRate,
  listRate,
  updateRate
} from '@/api/balan/rate'
import { getStationOptions } from '@/api/balan/station'
import {
  billingModeLabel,
  billingModeOptions,
  formatNumber,
  formatTime,
  periodTypeLabel,
  periodTypeOptions,
  strategyTypeLabel,
  strategyTypeOptions
} from '@/utils/balan'

defineOptions({ name: 'BalanRate' })

const loading = ref(false)
const showSearch = ref(true)
const rateList = ref([])
const total = ref(0)
const stationOptions = ref([])
const queryRef = ref()
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  stationId: undefined,
  keyword: undefined,
  strategyType: undefined,
  enabled: undefined
})

const summaryCards = computed(() => [
  { label: '策略总数', value: total.value, note: '当前查询范围' },
  { label: '当前生效', value: rateList.value.filter(item => item.current).length, note: '当前页' },
  { label: '服务费策略', value: rateList.value.filter(item => item.strategyType === 'SERVICE').length, note: '当前页' }
])

const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref()
const form = ref(defaultForm())
const rules = {
  stationId: [{ required: true, message: '请选择归属场站', trigger: 'change' }],
  name: [{ required: true, message: '策略名称不能为空', trigger: 'blur' }],
  billingMode: [{ required: true, message: '请选择计费模式', trigger: 'change' }]
}

const billingHint = computed(() => {
  if (form.value.strategyType === 'SERVICE') {
    return '仅服务费参与账单；启用后按生效日期叠加到同场站的电价策略上，时间费与电费仍由电价策略决定。'
  }
  if (form.value.billingMode === 'TIME') {
    return '仅累计时间费与服务费，电量照常记录但不计电费。'
  }
  if (form.value.billingMode === 'ENERGY') {
    return '按电量累计电费，服务费按配置单位计算，时间费不参与账单。'
  }
  return '时间费 + 电费 + 服务费全部计入订单金额。'
})

function defaultForm() {
  return {
    id: null,
    stationId: null,
    name: '',
    strategyType: 'ENERGY',
    billingMode: 'MIXED',
    effectiveRange: [],
    remark: '',
    periods: presetPeriods('ENERGY')
  }
}

function presetPeriods(strategyType = 'ENERGY') {
  const service = strategyType === 'SERVICE'
  return [
    { periodType: 'VALLEY', startTime: '22:00', endTime: '06:00', timePrice: service ? 0 : 0.5, energyPrice: service ? 0 : 0.3, servicePrice: 0.2, serviceUnit: 'KWH' },
    { periodType: 'FLAT', startTime: '06:00', endTime: '08:00', timePrice: service ? 0 : 0.7, energyPrice: service ? 0 : 0.6, servicePrice: 0.3, serviceUnit: 'KWH' },
    { periodType: 'PEAK', startTime: '08:00', endTime: '22:00', timePrice: service ? 0 : 1.0, energyPrice: service ? 0 : 0.9, servicePrice: 0.4, serviceUnit: 'KWH' }
  ]
}

function handleStrategyTypeChange(type) {
  if (type === 'SERVICE') {
    form.value.billingMode = 'MIXED'
    form.value.periods = form.value.periods.map(period => ({ ...period, timePrice: 0, energyPrice: 0 }))
  }
}

function getList() {
  loading.value = true
  listRate(queryParams).then(res => {
    rateList.value = (res.rows || []).map(item => ({ ...item, switching: false }))
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
  if (stationOptions.value.length === 1) {
    form.value.stationId = stationOptions.value[0].id
  }
  dialogVisible.value = true
}

function handleUpdate(row) {
  getRate(row.id).then(res => {
    const detail = res.data
    form.value = {
      id: detail.id,
      stationId: detail.stationId,
      name: detail.name,
      strategyType: detail.strategyType || 'ENERGY',
      billingMode: detail.billingMode,
      effectiveRange: detail.effectiveFrom || detail.effectiveTo
        ? [detail.effectiveFrom || '', detail.effectiveTo || '']
        : [],
      remark: detail.remark || '',
      periods: (detail.periods || []).map(period => ({
        periodType: period.periodType,
        startTime: period.startTime,
        endTime: period.endTime,
        timePrice: Number(period.timePrice || 0),
        energyPrice: Number(period.energyPrice || 0),
        servicePrice: Number(period.servicePrice || 0),
        serviceUnit: period.serviceUnit || 'KWH'
      }))
    }
    dialogVisible.value = true
  })
}

function applyPreset() {
  form.value.periods = presetPeriods(form.value.strategyType)
  ElMessage.success(form.value.strategyType === 'SERVICE'
    ? '已填入服务费示例，可按需调整'
    : '已填入峰平谷示例，可按需调整')
}

function addPeriod() {
  form.value.periods.push({
    periodType: 'FLAT',
    startTime: '00:00',
    endTime: '00:00',
    timePrice: 0,
    energyPrice: 0,
    servicePrice: 0,
    serviceUnit: 'KWH'
  })
}

function removePeriod(index) {
  form.value.periods.splice(index, 1)
}

function validatePeriods() {
  const periods = form.value.periods
  if (!periods.length) {
    ElMessage.warning('请至少配置一个费率时段')
    return false
  }
  for (const period of periods) {
    if (!period.periodType || !period.startTime || !period.endTime) {
      ElMessage.warning('请完整填写每个时段的类型和起止时间')
      return false
    }
    if ([period.timePrice, period.energyPrice, period.servicePrice].some(value => value === null || value === undefined || Number(value) < 0)) {
      ElMessage.warning('价格不能为空且不能小于 0')
      return false
    }
  }
  if (form.value.strategyType === 'SERVICE' && !periods.some(period => Number(period.servicePrice) > 0)) {
    ElMessage.warning('服务费策略至少需要一个时段配置大于 0 的服务费')
    return false
  }
  if (form.value.strategyType !== 'SERVICE'
    && !periods.some(period => Number(period.timePrice) > 0 || Number(period.energyPrice) > 0 || Number(period.servicePrice) > 0)) {
    ElMessage.warning('电价策略至少需要一项大于 0 的价格')
    return false
  }
  const [from, to] = form.value.effectiveRange || []
  if (from && to && to < from) {
    ElMessage.warning('生效结束日期不能早于开始日期')
    return false
  }
  return true
}

function submitForm() {
  formRef.value.validate(valid => {
    if (!valid || !validatePeriods()) {
      return
    }
    submitLoading.value = true
    const [effectiveFrom, effectiveTo] = form.value.effectiveRange || []
    const payload = {
      stationId: form.value.stationId,
      name: form.value.name,
      strategyType: form.value.strategyType,
      billingMode: form.value.billingMode,
      effectiveFrom: effectiveFrom || null,
      effectiveTo: effectiveTo || null,
      remark: form.value.remark,
      periods: form.value.periods.map(period => ({
        periodType: period.periodType,
        startTime: period.startTime,
        endTime: period.endTime,
        timePrice: Number(period.timePrice || 0),
        energyPrice: Number(period.energyPrice || 0),
        servicePrice: Number(period.servicePrice || 0),
        serviceUnit: period.serviceUnit
      }))
    }
    const request = form.value.id ? updateRate(form.value.id, payload) : addRate(payload)
    request.then(() => {
      ElMessage.success(form.value.id ? '费率策略已更新' : '费率策略已创建，请确认后启用')
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

function toggleEnabled(row, enabled) {
  if (!enabled) {
    doToggle(row, false)
    return
  }
  const conflicts = overlappingSiblings(row)
  const tip = conflicts.length
    ? `启用后，同场站同类型且生效日期重叠的 ${conflicts.length} 套策略将自动停用：${conflicts.map(item => item.name).join('、')}。继续吗？`
    : '启用后，该策略将在生效日期区间内参与计费。继续吗？'
  ElMessageBox.confirm(tip, '启用策略', {
    type: 'warning',
    confirmButtonText: '启用',
    cancelButtonText: '取消'
  }).then(() => doToggle(row, true)).catch(() => {})
}

function doToggle(row, enabled) {
  row.switching = true
  const request = enabled ? enableRate(row.id) : disableRate(row.id)
  request.then(() => {
    ElMessage.success(enabled ? '策略已启用，重叠策略已自动停用' : '策略已停用')
    getList()
  }).finally(() => {
    row.switching = false
  })
}

function overlappingSiblings(row) {
  const type = row.strategyType || 'ENERGY'
  const from = row.effectiveFrom || '0000-01-01'
  const to = row.effectiveTo || '9999-12-31'
  return rateList.value.filter(item => {
    if (item.id === row.id || item.enabled !== 1 || item.stationId !== row.stationId) {
      return false
    }
    if ((item.strategyType || 'ENERGY') !== type) {
      return false
    }
    const otherFrom = item.effectiveFrom || '0000-01-01'
    const otherTo = item.effectiveTo || '9999-12-31'
    return otherFrom <= to && from <= otherTo
  })
}

function effectiveRangeLabel(row) {
  if (!row.effectiveFrom && !row.effectiveTo) {
    return '长期有效'
  }
  return `${row.effectiveFrom || '不限'} 至 ${row.effectiveTo || '不限'}`
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除费率策略「${row.name}」吗？`, '删除提示', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(() => delRate(row.id)).then(() => {
    ElMessage.success('策略已删除')
    getList()
  }).catch(() => {})
}

function periodWindow(period) {
  if (!period.startTime || !period.endTime) {
    return '-'
  }
  if (period.startTime === period.endTime) {
    return '全天'
  }
  const overnight = period.endTime < period.startTime
  return `${period.startTime} - ${overnight ? '次日 ' : ''}${period.endTime}`
}

function periodTagType(type) {
  return { PEAK: 'danger', FLAT: 'warning', VALLEY: 'success' }[type] || 'info'
}

onMounted(() => {
  loadStationOptions()
  getList()
})
</script>

<style scoped lang="scss">
.rate-page {
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

  .name-line {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .period-tags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
  }

  .period-panel {
    padding: 6px 18px 12px;
    background: #f8fafc;
  }
}

.rate-dialog {
  .muted {
    color: #94a3b8;
    font-size: 12px;
  }

  .period-editor-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin: 6px 0 10px;
    padding-top: 12px;
    border-top: 1px dashed #e2e8f0;
  }

  .panel-title {
    display: block;
    margin-bottom: 4px;
    color: #0f172a;
    font-weight: 600;
  }

  .period-actions {
    display: flex;
    flex-shrink: 0;
    gap: 8px;
  }

  .period-edit-table {
    margin-bottom: 12px;
  }

  .billing-hint {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 12px;
    border-radius: 8px;
    background: #f8fafc;
    color: #64748b;
    font-size: 13px;
  }
}
</style>

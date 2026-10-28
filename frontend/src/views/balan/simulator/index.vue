<template>
  <div class="app-container simulator-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">DEVELOPER · SIMULATOR</p>
        <h2>仿真控制</h2>
        <p class="page-desc">内置 MQTT 虚拟插座，驱动 2 / 10 / 12 路设备完成端到端演示，生产环境可关闭。</p>
      </div>
      <el-button icon="Refresh" @click="refreshAll">刷新</el-button>
    </div>

    <el-card shadow="never" class="status-card">
      <div class="status-main">
        <div class="status-indicator" :class="{ live: status.running }"></div>
        <div>
          <div class="status-title">
            <h3>{{ status.running ? '仿真器运行中' : '仿真器已停止' }}</h3>
            <el-tag :type="status.running ? 'success' : 'info'" effect="plain">
              {{ status.running ? 'RUNNING' : 'STOPPED' }}
            </el-tag>
            <el-tag :type="status.mqttConnected ? 'success' : 'danger'" effect="plain">
              MQTT {{ status.mqttConnected ? '已连接' : '未连接' }}
            </el-tag>
          </div>
          <p class="muted">
            Broker：{{ status.brokerUrl || '-' }}
            <span class="divider">·</span>
            上报间隔：{{ status.intervalSeconds || '-' }}s
            <span class="divider">·</span>
            最近上报：{{ formatTime(status.lastPublishAt) }}
          </p>
        </div>
      </div>
      <div class="status-stats">
        <div><strong>{{ status.deviceCount || 0 }}</strong><span>仿真设备</span></div>
        <div><strong>{{ status.portCount || 0 }}</strong><span>端口</span></div>
      </div>
      <div class="status-actions">
        <el-button
          v-hasPermi="['simulator:start']"
          type="primary"
          icon="VideoPlay"
          :loading="actionLoading"
          :disabled="status.running"
          @click="handleStart"
        >启动仿真</el-button>
        <el-button
          v-hasPermi="['simulator:stop']"
          type="danger"
          plain
          icon="VideoPause"
          :loading="actionLoading"
          :disabled="!status.running"
          @click="handleStop"
        >停止仿真</el-button>
      </div>
    </el-card>

    <el-row :gutter="14" class="control-row">
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-head">
              <span>运行参数</span>
              <el-tag size="small" type="info" effect="plain">sim_config</el-tag>
            </div>
          </template>
          <el-form :model="configForm" label-width="96px">
            <el-form-item label="上报间隔">
              <el-input-number v-model="configForm.intervalSeconds" :min="1" :max="60" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="故障概率">
              <el-input-number v-model="configForm.faultProbability" :min="0" :max="1" :step="0.005" :precision="4" controls-position="right" style="width: 100%" />
            </el-form-item>
            <p class="form-tip">每次端口上报时按该概率随机进入故障，默认 0.02 约等于每 5 秒有 2% 概率。</p>
            <el-button
              v-hasPermi="['simulator:config']"
              type="primary"
              plain
              :loading="configLoading"
              @click="handleSaveConfig"
            >保存参数</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-head">
              <span>批量创建设备</span>
              <el-tag size="small" type="info" effect="plain">最多 20 台</el-tag>
            </div>
          </template>
          <el-form :model="batchForm" label-width="86px">
            <el-form-item label="归属场站">
              <el-select v-model="batchForm.stationId" placeholder="请选择场站" style="width: 100%">
                <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="设备类型">
              <el-select v-model="batchForm.deviceType" style="width: 100%">
                <el-option v-for="item in deviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-row :gutter="10">
              <el-col :span="10">
                <el-form-item label="数量" label-width="58px">
                  <el-input-number v-model="batchForm.count" :min="1" :max="20" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="14">
                <el-form-item label="名称前缀" label-width="74px">
                  <el-input v-model="batchForm.namePrefix" maxlength="40" placeholder="仿真设备" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-button
              v-hasPermi="['simulator:start']"
              type="primary"
              plain
              :loading="batchLoading"
              @click="handleBatchCreate"
            >创建并上线</el-button>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-head">
              <span>故障注入</span>
              <el-tag size="small" type="warning" effect="plain">演示用</el-tag>
            </div>
          </template>
          <el-form v-if="deviceOptions.length" :model="faultForm" label-width="86px">
            <el-form-item label="仿真设备">
              <el-select v-model="faultForm.deviceNo" placeholder="请选择设备" style="width: 100%" @change="handleDeviceChange">
                <el-option
                  v-for="item in deviceOptions"
                  :key="item.deviceNo"
                  :label="`${item.deviceNo} · ${item.name}`"
                  :value="item.deviceNo"
                />
              </el-select>
            </el-form-item>
            <el-row :gutter="10">
              <el-col :span="10">
                <el-form-item label="端口" label-width="58px">
                  <el-select v-model="faultForm.portNo" style="width: 100%">
                    <el-option v-for="portNo in portOptions" :key="portNo" :label="`#${portNo}`" :value="portNo" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="14">
                <el-form-item label="持续秒数" label-width="74px">
                  <el-input-number v-model="faultForm.durationSeconds" :min="5" :max="300" :step="5" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="故障类型">
              <el-select v-model="faultForm.faultType" style="width: 100%">
                <el-option label="过温 OVER_TEMP" value="OVER_TEMP" />
              </el-select>
            </el-form-item>
            <el-button
              v-hasPermi="['simulator:fault']"
              type="warning"
              plain
              :loading="faultLoading"
              :disabled="!status.running"
              @click="handleInjectFault"
            >注入故障</el-button>
          </el-form>
          <el-empty v-else description="暂无仿真设备，请先批量创建" :image-size="64" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="log-card">
      <template #header>
        <div class="log-head">
          <div class="panel-head">
            <span>MQTT 报文</span>
            <el-tag size="small" type="info" effect="plain">内存 {{ logs.length }} 条</el-tag>
          </div>
          <div class="log-actions">
            <el-switch v-model="autoRefresh" inline-prompt active-text="自动" inactive-text="手动" />
            <el-select v-model="logQuery.deviceNo" placeholder="全部设备" clearable style="width: 190px" @change="loadLogs">
              <el-option v-for="item in deviceOptions" :key="item.deviceNo" :label="item.deviceNo" :value="item.deviceNo" />
            </el-select>
            <el-select v-model="logQuery.limit" style="width: 110px" @change="loadLogs">
              <el-option :value="20" label="最近 20" />
              <el-option :value="50" label="最近 50" />
              <el-option :value="100" label="最近 100" />
              <el-option :value="200" label="最近 200" />
            </el-select>
            <el-button icon="Refresh" :loading="logLoading" @click="loadLogs">刷新</el-button>
            <el-button v-hasPermi="['simulator:config']" icon="Delete" @click="handleClearLogs">清空</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="logLoading" :data="logs" size="small" max-height="420">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatTime(row.time) }}</template>
        </el-table-column>
        <el-table-column label="方向" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'IN' ? 'success' : 'warning'" size="small" effect="plain">
              {{ row.direction === 'IN' ? '上行' : '下行' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deviceNo" label="设备" width="140" />
        <el-table-column prop="topic" label="Topic" min-width="260" show-overflow-tooltip />
        <el-table-column label="Payload" min-width="320">
          <template #default="{ row }">
            <span class="payload-preview">{{ preview(row.payload) }}</span>
            <el-button link type="primary" size="small" @click="showPayload(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="payloadVisible" title="MQTT Payload" width="760px" append-to-body>
      <div class="payload-meta">
        <span>{{ payloadDetail.topic }}</span>
        <span>{{ formatTime(payloadDetail.time) }}</span>
      </div>
      <pre class="payload-full">{{ payloadDetail.payload }}</pre>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  batchCreateSimulated,
  clearMqttLogs,
  getSimulatorStatus,
  injectFault,
  listMqttLogs,
  startSimulator,
  stopSimulator,
  updateSimulatorConfig
} from '@/api/balan/simulator'
import { listCharger } from '@/api/balan/charger'
import { getStationOptions } from '@/api/balan/station'
import { deviceTypeOptions, formatTime } from '@/utils/balan'

defineOptions({ name: 'BalanSimulator' })

const status = ref({})
const stationOptions = ref([])
const deviceOptions = ref([])
const actionLoading = ref(false)
const configLoading = ref(false)
const batchLoading = ref(false)
const faultLoading = ref(false)
const logLoading = ref(false)
const autoRefresh = ref(true)
const logs = ref([])
let configLoaded = false
let statusTimer = null
let logTimer = null

const configForm = reactive({
  intervalSeconds: 5,
  faultProbability: 0.02
})

const batchForm = reactive({
  stationId: null,
  deviceType: 'SOCKET_12',
  count: 1,
  namePrefix: '仿真设备'
})

const faultForm = reactive({
  deviceNo: '',
  portNo: 1,
  durationSeconds: 30,
  faultType: 'OVER_TEMP'
})

const logQuery = reactive({
  deviceNo: undefined,
  limit: 50
})

const payloadVisible = ref(false)
const payloadDetail = ref({})

const selectedDevice = computed(() => deviceOptions.value.find(item => item.deviceNo === faultForm.deviceNo))
const portOptions = computed(() => {
  const count = selectedDevice.value?.portCount || 0
  return Array.from({ length: count }, (_, index) => index + 1)
})

function loadStatus() {
  return getSimulatorStatus().then(res => {
    status.value = res.data || {}
    if (!configLoaded) {
      configForm.intervalSeconds = status.value.intervalSeconds || 5
      configForm.faultProbability = Number(status.value.faultProbability ?? 0.02)
      configLoaded = true
    }
  })
}

function loadStations() {
  getStationOptions().then(res => {
    stationOptions.value = res.data || []
    if (!batchForm.stationId && stationOptions.value.length) {
      batchForm.stationId = stationOptions.value[0].id
    }
  })
}

function loadDevices() {
  listCharger({ pageNum: 1, pageSize: 100 }).then(res => {
    deviceOptions.value = (res.rows || []).filter(item => item.isSimulated === 1)
    if (!faultForm.deviceNo && deviceOptions.value.length) {
      faultForm.deviceNo = deviceOptions.value[0].deviceNo
      faultForm.portNo = 1
    }
  })
}

function loadLogs() {
  logLoading.value = true
  listMqttLogs({
    deviceNo: logQuery.deviceNo,
    limit: logQuery.limit
  }).then(res => {
    logs.value = res.data || []
  }).finally(() => {
    logLoading.value = false
  })
}

function refreshAll() {
  loadStatus()
  loadDevices()
  loadLogs()
}

function handleStart() {
  actionLoading.value = true
  startSimulator().then(res => {
    status.value = res.data || {}
    ElMessage.success('仿真器已启动')
    loadDevices()
  }).finally(() => {
    actionLoading.value = false
  })
}

function handleStop() {
  actionLoading.value = true
  stopSimulator().then(res => {
    status.value = res.data || {}
    ElMessage.success('仿真器已停止，设备将在心跳超时后离线')
  }).finally(() => {
    actionLoading.value = false
  })
}

function handleSaveConfig() {
  configLoading.value = true
  updateSimulatorConfig({
    intervalSeconds: configForm.intervalSeconds,
    faultProbability: configForm.faultProbability
  }).then(res => {
    status.value = res.data || {}
    ElMessage.success('仿真参数已保存')
  }).finally(() => {
    configLoading.value = false
  })
}

function handleBatchCreate() {
  if (!batchForm.stationId) {
    ElMessage.warning('请选择归属场站')
    return
  }
  batchLoading.value = true
  batchCreateSimulated({
    stationId: batchForm.stationId,
    deviceType: batchForm.deviceType,
    count: batchForm.count,
    namePrefix: batchForm.namePrefix
  }).then(res => {
    const created = res.data || []
    ElMessage.success(`已创建 ${created.length} 台仿真设备`)
    loadDevices()
    loadStatus()
    loadLogs()
  }).finally(() => {
    batchLoading.value = false
  })
}

function handleDeviceChange() {
  faultForm.portNo = 1
}

function handleInjectFault() {
  if (!faultForm.deviceNo) {
    ElMessage.warning('请选择仿真设备')
    return
  }
  faultLoading.value = true
  injectFault({
    deviceNo: faultForm.deviceNo,
    portNo: faultForm.portNo,
    durationSeconds: faultForm.durationSeconds,
    faultType: faultForm.faultType
  }).then(res => {
    status.value = res.data || {}
    ElMessage.success(`已向 ${faultForm.deviceNo} / 端口 #${faultForm.portNo} 注入 ${faultForm.faultType}`)
    loadLogs()
  }).finally(() => {
    faultLoading.value = false
  })
}

function handleClearLogs() {
  ElMessageBox.confirm('确定清空内存中的 MQTT 报文吗？', '清空提示', {
    type: 'warning',
    confirmButtonText: '清空',
    cancelButtonText: '取消'
  }).then(() => clearMqttLogs()).then(() => {
    logs.value = []
    ElMessage.success('报文已清空')
  }).catch(() => {})
}

function preview(payload) {
  if (!payload) {
    return '-'
  }
  return payload.length > 110 ? payload.slice(0, 110) + '...' : payload
}

function showPayload(row) {
  payloadDetail.value = row
  payloadVisible.value = true
}

onMounted(() => {
  loadStatus()
  loadStations()
  loadDevices()
  loadLogs()
  statusTimer = setInterval(loadStatus, 5000)
  logTimer = setInterval(() => {
    if (autoRefresh.value) {
      loadLogs()
    }
  }, 3000)
})

onBeforeUnmount(() => {
  clearInterval(statusTimer)
  clearInterval(logTimer)
})
</script>

<style scoped lang="scss">
.simulator-page {
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

  .status-card {
    margin-bottom: 14px;
    border: 1px solid #e2e8f0;
    border-radius: 12px;

    :deep(.el-card__body) {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 24px;
      flex-wrap: wrap;
      padding: 20px 22px;
      background: linear-gradient(120deg, #f8fafc, #fff 55%);
    }
  }

  .status-main {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .status-indicator {
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: #cbd5e1;
    box-shadow: 0 0 0 6px rgba(148, 163, 184, 0.18);

    &.live {
      background: #10b981;
      box-shadow: 0 0 0 6px rgba(16, 185, 129, 0.16);
      animation: pulse 1.8s infinite;
    }
  }

  @keyframes pulse {
    0%, 100% { box-shadow: 0 0 0 6px rgba(16, 185, 129, 0.16); }
    50% { box-shadow: 0 0 0 10px rgba(16, 185, 129, 0.06); }
  }

  .status-title {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;

    h3 {
      margin: 0;
      color: #0f172a;
      font-size: 20px;
    }
  }

  .muted {
    color: #94a3b8;
    font-size: 12px;
  }

  .divider {
    margin: 0 6px;
    color: #cbd5e1;
  }

  .status-stats {
    display: flex;
    gap: 32px;

    div {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 2px;
    }

    strong {
      color: #0f172a;
      font-size: 24px;
    }

    span {
      color: #94a3b8;
      font-size: 12px;
    }
  }

  .status-actions {
    display: flex;
    gap: 10px;
  }

  .control-row {
    .el-col {
      margin-bottom: 14px;
    }
  }

  .panel-card {
    height: 100%;
    border: 1px solid #e2e8f0;
    border-radius: 12px;
  }

  .panel-head {
    display: flex;
    align-items: center;
    gap: 10px;
    color: #0f172a;
    font-weight: 600;
  }

  .form-tip {
    margin: -4px 0 14px;
    color: #94a3b8;
    font-size: 12px;
    line-height: 1.6;
  }

  .log-card {
    border: 1px solid #e2e8f0;
    border-radius: 12px;
  }

  .log-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 16px;
    flex-wrap: wrap;
  }

  .log-actions {
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
  }

  .payload-preview {
    color: #475569;
    font-family: Consolas, Monaco, monospace;
    font-size: 12px;
  }

  .payload-meta {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 10px;
    color: #64748b;
    font-size: 12px;
    word-break: break-all;
  }

  .payload-full {
    max-height: 460px;
    margin: 0;
    padding: 14px;
    overflow: auto;
    border-radius: 8px;
    background: #0f172a;
    color: #e2e8f0;
    font-family: Consolas, Monaco, monospace;
    font-size: 12px;
    line-height: 1.6;
    white-space: pre-wrap;
    word-break: break-all;
  }
}
</style>

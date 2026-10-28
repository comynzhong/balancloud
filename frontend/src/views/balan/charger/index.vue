<template>
  <div class="app-container charger-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">OPERATIONS · CHARGER</p>
        <h2>充电桩管理</h2>
        <p class="page-desc">按“设备 + 端口”管理多路插座，实时值来自 MQTT 设备影子，历史曲线来自 TDengine。</p>
      </div>
      <div class="page-actions">
        <el-button icon="Refresh" @click="getList">刷新</el-button>
        <el-button v-hasPermi="['charger:add']" type="primary" icon="Plus" @click="handleAdd">新增设备</el-button>
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
        <el-form-item label="归属场站" prop="stationId">
          <el-select v-model="queryParams.stationId" placeholder="全部场站" clearable style="width: 200px">
            <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="设备编号 / 名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="在线状态" prop="onlineStatus">
          <el-select v-model="queryParams.onlineStatus" placeholder="全部状态" clearable style="width: 140px">
            <el-option label="在线" value="ONLINE" />
            <el-option label="离线" value="OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备分组" prop="groupName">
          <el-select v-model="queryParams.groupName" placeholder="全部分组" clearable style="width: 160px">
            <el-option v-for="item in groupOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="toolbar">
        <el-col :span="1.5">
          <el-tag type="info" effect="plain">共 {{ total }} 台设备</el-tag>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="chargerList" row-key="id" @row-dblclick="openMonitor">
        <el-table-column label="设备" min-width="190">
          <template #default="{ row }">
            <div class="device-line">
              <span class="strong">{{ row.deviceNo }}</span>
              <el-tag v-if="row.isSimulated === 1" size="small" type="warning" effect="plain">仿真</el-tag>
            </div>
            <div class="muted">{{ row.name }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="stationName" label="归属场站" min-width="150" show-overflow-tooltip />
        <el-table-column label="分组" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.groupName" size="small" effect="plain">{{ row.groupName }}</el-tag>
            <span v-else class="muted">未分组</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ deviceTypeLabel(row.deviceType) }}</template>
        </el-table-column>
        <el-table-column label="协议" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.protocol === 'OCPP16J'" size="small" type="success" effect="plain">{{ protocolLabel(row.protocol) }}</el-tag>
            <span v-else>{{ protocolLabel(row.protocol) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="场景" width="110">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ sceneLabel(row.scene) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启动方式" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.startMethod === 'PLUG_AND_CHARGE'" size="small" type="warning" effect="plain">
              {{ startMethodLabel(row.startMethod) }}
            </el-tag>
            <span v-else>{{ startMethodLabel(row.startMethod) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="在线状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.onlineStatus === 'ONLINE' ? 'success' : 'info'" effect="plain" size="small">
              {{ row.onlineStatus === 'ONLINE' ? '在线' : '离线' }}
            </el-tag>
            <div class="muted">{{ formatTime(row.lastOnlineAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="端口状态" width="170" align="center">
          <template #default="{ row }">
            <div class="port-tags">
              <el-tag size="small" type="success" effect="plain">充 {{ row.chargingPorts || 0 }}</el-tag>
              <el-tag size="small" type="info" effect="plain">闲 {{ row.idlePorts || 0 }}</el-tag>
              <el-tag size="small" type="danger" effect="plain">障 {{ row.faultPorts || 0 }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="实时功率" width="100" align="right">
          <template #default="{ row }">
            <span class="power-value">{{ formatNumber(row.currentPowerKw, 2) }}</span>
            <span class="muted"> kW</span>
          </template>
        </el-table-column>
        <el-table-column label="最高温度" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'temp-warning': row.maxTemperature > Number(row.overTempC) }">
              {{ formatNumber(row.maxTemperature, 1) }} °C
            </span>
          </template>
        </el-table-column>
        <el-table-column label="最近上报" width="120">
          <template #default="{ row }">
            <span class="muted">{{ formatTime(row.lastMetricAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" icon="DataLine" @click="openMonitor(row)">监控</el-button>
            <el-button v-hasPermi="['charger:edit']" link type="primary" icon="Edit" @click="handleUpdate(row)">编辑</el-button>
            <el-button v-hasPermi="['charger:remove']" link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑设备' : '新增设备'" width="700px" append-to-body @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="102px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="归属场站" prop="stationId">
              <el-select v-model="form.stationId" placeholder="请选择场站" style="width: 100%">
                <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类型" prop="deviceType">
              <el-select v-model="form.deviceType" :disabled="!!form.id" style="width: 100%" @change="handleDeviceTypeChange">
                <el-option v-for="item in deviceTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="通信协议" prop="protocol">
              <el-select v-model="form.protocol" :disabled="!!form.id" style="width: 100%" @change="handleProtocolChange">
                <el-option v-for="item in protocolOptions" :key="item.protocol" :label="protocolLabel(item.protocol)" :value="item.protocol" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="订单场景" prop="scene">
              <el-select v-model="form.scene" style="width: 100%" @change="handleSceneChange">
                <el-option v-for="item in sceneOptions" :key="item.scene" :label="sceneLabel(item.scene)" :value="item.scene" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启动方式" prop="startMethod">
              <el-select v-model="form.startMethod" :disabled="form.scene !== 'AC_CAR'" style="width: 100%">
                <el-option v-for="item in startMethodOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <div v-if="form.scene === 'AC_CAR'" class="muted form-hint">
                插枪即充：桩直接上报 StartTransaction，平台按 VIN 反查车主建单
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备编号" prop="deviceNo">
              <el-input
                v-model="form.deviceNo"
                :placeholder="form.protocol === 'OCPP16J' ? 'ChargePoint identity，如 CP-DC-001' : 'MQTT deviceId，如 BC-S12-002'"
                maxlength="64"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" prop="name">
              <el-input v-model="form.name" placeholder="如 1号插座群" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备分组" prop="groupName">
              <el-select
                v-model="form.groupName"
                filterable
                allow-create
                default-first-option
                clearable
                placeholder="如 A 区（可自定义）"
                style="width: 100%"
              >
                <el-option v-for="item in groupOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="固件版本" prop="firmwareVersion">
              <el-input v-model="form.firmwareVersion" placeholder="如 v1.0.0" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="额定电压" prop="ratedVoltage">
              <el-input-number v-model="form.ratedVoltage" :min="1" :precision="2" :step="10" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="单口功率" prop="ratedPowerKw">
              <el-input-number v-model="form.ratedPowerKw" :min="0.001" :precision="3" :step="0.1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="过温阈值" prop="overTempC">
              <el-input-number v-model="form.overTempC" :min="1" :precision="1" :step="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="过流阈值" prop="overCurrentA">
              <el-input-number v-model="form.overCurrentA" :min="0.01" :precision="1" :step="0.5" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="仿真设备" prop="isSimulated">
              <el-switch v-model="form.isSimulated" :active-value="1" :inactive-value="0" active-text="是" inactive-text="否" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>

    <el-drawer
      v-model="monitorVisible"
      size="82%"
      :destroy-on-close="true"
      @opened="handleDrawerOpened"
      @closed="handleDrawerClosed"
    >
      <template #header>
        <div class="monitor-header">
          <div>
            <div class="device-line">
              <h3>{{ detail?.charger?.deviceNo || '设备监控' }}</h3>
              <el-tag v-if="detail" :type="detail.charger.onlineStatus === 'ONLINE' ? 'success' : 'info'" effect="plain">
                {{ detail.charger.onlineStatus === 'ONLINE' ? '在线' : '离线' }}
              </el-tag>
              <el-tag v-if="detail?.charger?.scene" type="info" effect="plain">{{ sceneLabel(detail.charger.scene) }}</el-tag>
              <el-tag v-if="detail?.charger?.startMethod === 'PLUG_AND_CHARGE'" type="warning" effect="plain">插枪即充</el-tag>
            </div>
            <p class="muted">{{ detail?.charger?.name }} · {{ detail?.charger?.stationName }}</p>
          </div>
          <div class="monitor-meta">
            <span>端口状态每 5 秒刷新</span>
            <span>曲线每 30 秒刷新</span>
          </div>
        </div>
      </template>

      <div v-loading="monitorLoading" class="monitor-body">
        <template v-if="detail">
          <el-row :gutter="12" class="monitor-summary">
            <el-col :xs="12" :sm="6">
              <div class="mini-stat">
                <span>实时功率</span>
                <strong>{{ formatNumber(detail.charger.currentPowerKw, 2) }} <small>kW</small></strong>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="mini-stat">
                <span>充电中 / 空闲 / 故障</span>
                <strong>{{ detail.charger.chargingPorts }} / {{ detail.charger.idlePorts }} / {{ detail.charger.faultPorts }}</strong>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="mini-stat">
                <span>最高温度</span>
                <strong>{{ formatNumber(detail.charger.maxTemperature, 1) }} <small>°C</small></strong>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="mini-stat">
                <span>最近上报</span>
                <strong class="time">{{ formatTime(detail.charger.lastMetricAt) }}</strong>
              </div>
            </el-col>
          </el-row>

          <div class="section-title">
            <span>端口矩阵</span>
            <el-tag size="small" type="info" effect="plain">{{ detail.ports.length }} 个端口</el-tag>
            <span v-if="preChargeHint" class="muted">启动前需经过：{{ preChargeHint }}</span>
          </div>
          <div class="port-toolbar">
            <div class="port-selection">
              <span>当前端口</span>
              <strong>#{{ selectedPort || '-' }}</strong>
              <el-tag v-if="selectedPortData" size="small" :type="portStatusType(selectedPortData.status)" effect="plain">
                {{ portStatusLabel(selectedPortData.status) }}
              </el-tag>
              <span v-if="selectedPortData?.orderNo" class="port-order-no">
                订单 {{ selectedPortData.orderNo }}
                <template v-if="selectedPortData.appUserNickname">
                  · {{ selectedPortData.appUserNickname }}
                  <template v-if="selectedPortData.appUserPhone">（{{ selectedPortData.appUserPhone }}）</template>
                </template>
                <template v-if="selectedPortData.vehiclePlateNo"> · {{ selectedPortData.vehiclePlateNo }}</template>
                <template v-if="selectedPortData.orderStartTime">
                  · 已充 {{ formatDuration(selectedPortData.orderDurationSeconds) }}
                </template>
              </span>
              <span v-if="detail.charger.startMethod === 'PLUG_AND_CHARGE'" class="muted">
                插枪即充：由桩自动建单，无需人工启动
              </span>
            </div>
            <div class="port-actions">
              <el-button
                v-if="selectedPortData && ['IDLE', 'OCCUPIED'].includes(selectedPortData.status)"
                v-hasPermi="['charger:start']"
                type="success"
                plain
                icon="VideoPlay"
                :disabled="detail.charger.onlineStatus !== 'ONLINE' || detail.charger.startMethod === 'PLUG_AND_CHARGE'"
                @click="openStart"
              >启动充电</el-button>
              <el-button
                v-if="selectedPortData?.status === 'OCCUPIED' && detail.charger?.isSimulated === 1"
                v-hasPermi="['charger:unplug']"
                type="warning"
                plain
                icon="SwitchButton"
                :disabled="detail.charger.onlineStatus !== 'ONLINE'"
                @click="handleSimulatedUnplug"
              >模拟拔枪</el-button>
              <el-button
                v-if="ocppSimVisible"
                v-hasPermi="['charger:edit']"
                :type="ocppSimRunning ? 'warning' : 'primary'"
                plain
                icon="Monitor"
                :loading="ocppSimLoading"
                @click="toggleOcppSim"
              >{{ ocppSimRunning ? '停止 OCPP 模拟' : '启动 OCPP 模拟' }}</el-button>
              <el-button
                v-if="selectedPortData?.status === 'CHARGING'"
                v-hasPermi="['charger:stop']"
                type="danger"
                plain
                icon="VideoPause"
                :disabled="detail.charger.onlineStatus !== 'ONLINE'"
                @click="handleStop"
              >停止充电</el-button>
              <el-button
                v-hasPermi="['charger:unlock']"
                icon="Unlock"
                :disabled="detail.charger.onlineStatus !== 'ONLINE' || selectedPortData?.status === 'CHARGING'"
                @click="handleUnlock"
              >远程解锁</el-button>
              <el-button
                v-hasPermi="['charger:threshold']"
                icon="SetUp"
                :disabled="detail.charger.onlineStatus !== 'ONLINE'"
                @click="openThreshold"
              >保护阈值</el-button>
            </div>
          </div>
          <div class="port-grid">
            <div
              v-for="port in detail.ports"
              :key="port.portNo"
              class="port-card"
              :class="[port.status.toLowerCase(), { active: selectedPort === port.portNo }]"
              @click="selectPort(port.portNo)"
            >
              <div class="port-top">
                <span class="port-no">#{{ port.portNo }}</span>
                <el-tag size="small" :type="portStatusType(port.status)" effect="plain">{{ portStatusLabel(port.status) }}</el-tag>
              </div>
              <div class="port-power">{{ formatNumber(port.powerKw, 2) }} <small>kW</small></div>
              <div class="port-lines">
                <span>U {{ formatNumber(port.voltage, 1) }} V</span>
                <span>I {{ formatNumber(port.currentA, 1) }} A</span>
                <span>T {{ formatNumber(port.temperature, 1) }} °C</span>
              </div>
              <div class="port-energy">累计 {{ formatNumber(port.energyTotal, 2) }} kWh</div>
              <div v-if="port.orderNo" class="port-order">
                订单 {{ port.orderNo }}
                <template v-if="port.appUserNickname"> · {{ port.appUserNickname }}</template>
                <template v-if="port.vehiclePlateNo"> · {{ port.vehiclePlateNo }}</template>
                <div v-if="port.orderStartTime" class="port-user">
                  {{ formatDuration(port.orderDurationSeconds) }}
                </div>
              </div>
            </div>
          </div>

          <el-card shadow="never" class="chart-card">
            <template #header>
              <div class="chart-head">
                <span>端口 #{{ selectedPort }} · 近 30 分钟曲线</span>
                <div class="chart-actions">
                  <el-tag size="small" type="info" effect="plain">TDengine</el-tag>
                  <el-button size="small" icon="Refresh" :loading="historyLoading" @click="loadHistory()">刷新曲线</el-button>
                </div>
              </div>
            </template>
            <div v-if="history.length > 0" ref="chartRef" class="history-chart"></div>
            <el-empty v-else v-loading="historyLoading" description="暂无历史数据，等待设备上报" :image-size="70" />
          </el-card>
        </template>
      </div>
    </el-drawer>

    <el-dialog v-model="chargeDialogVisible" title="启动充电" width="480px" append-to-body :close-on-click-modal="false">
      <el-form ref="chargeFormRef" :model="chargeForm" :rules="chargeRules" label-width="92px">
        <el-form-item label="目标端口">
          <el-tag effect="plain">#{{ selectedPort }} 号口</el-tag>
          <span class="dialog-hint">{{ detail?.charger?.deviceNo }}</span>
        </el-form-item>
        <el-form-item label="最大时长" prop="maxDurationMinutes">
          <el-input-number v-model="chargeForm.maxDurationMinutes" :min="1" :max="1440" :step="10" controls-position="right" style="width: 180px" />
          <span class="dialog-hint">分钟</span>
        </el-form-item>
        <el-form-item label="金额上限" prop="chargeLimitAmount">
          <el-input-number v-model="chargeForm.chargeLimitAmount" :min="0.01" :max="100000" :precision="2" :step="5" controls-position="right" style="width: 180px" />
          <span class="dialog-hint">元</span>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="订单将按场站当前启用的费率策略计费，达到时长或金额上限后平台自动下发停止。"
        />
      </el-form>
      <template #footer>
        <el-button @click="chargeDialogVisible = false">取消</el-button>
        <el-button type="success" :loading="actionLoading" @click="submitStart">确认启动</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="thresholdDialogVisible" title="下发保护阈值" width="460px" append-to-body :close-on-click-modal="false">
      <el-form ref="thresholdFormRef" :model="thresholdForm" :rules="thresholdRules" label-width="100px">
        <el-form-item label="目标端口">
          <el-tag effect="plain">#{{ selectedPort }} 号口</el-tag>
          <span class="dialog-hint">{{ detail?.charger?.deviceNo }}</span>
        </el-form-item>
        <el-form-item label="过温阈值" prop="overTempC">
          <el-input-number v-model="thresholdForm.overTempC" :min="30" :max="120" :precision="1" :step="1" controls-position="right" style="width: 180px" />
          <span class="dialog-hint">°C（30 ~ 120）</span>
        </el-form-item>
        <el-form-item label="过流阈值" prop="overCurrentA">
          <el-input-number v-model="thresholdForm.overCurrentA" :min="1" :max="63" :precision="1" :step="0.5" controls-position="right" style="width: 180px" />
          <span class="dialog-hint">A（1 ~ 63）</span>
        </el-form-item>
        <el-alert type="warning" :closable="false" show-icon title="阈值先写入平台设备档案，再通过 MQTT 下发到设备；设备离线时无法下发。" />
      </el-form>
      <template #footer>
        <el-button @click="thresholdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionLoading" @click="submitThreshold">确认下发</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addCharger,
  delCharger,
  getCharger,
  getChargerGroups,
  getChargerHistory,
  getChargerScenes,
  getDeviceProtocols,
  listCharger,
  updateCharger
} from '@/api/balan/charger'
import { setPortThreshold, startCharging, stopCharging, unlockPort } from '@/api/balan/charging'
import { getStationOptions } from '@/api/balan/station'
import { unplugSimulated, getOcppSimulatorStatus, startOcppSimulator, stopOcppSimulator } from '@/api/balan/simulator'
import {
  deviceTypeLabel,
  deviceTypeOptions,
  formatDuration,
  formatNumber,
  formatTime,
  portStatusLabel,
  portStatusType,
  preChargePhaseLabel,
  protocolLabel,
  sceneLabel,
  startMethodLabel,
  startMethodOptions
} from '@/utils/balan'

defineOptions({ name: 'BalanCharger' })

const loading = ref(false)
const showSearch = ref(true)
const chargerList = ref([])
const total = ref(0)
const stationOptions = ref([])
const groupOptions = ref([])
const sceneOptions = ref([])
const protocolOptions = ref([])
const queryRef = ref()
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  stationId: undefined,
  keyword: undefined,
  onlineStatus: undefined,
  groupName: undefined
})

const summaryCards = computed(() => [
  { label: '设备总数', value: total.value, note: '当前查询范围' },
  { label: '在线设备', value: countOnline(), note: 'MQTT 心跳正常' },
  { label: '充电中端口', value: sumOf('chargingPorts'), note: '当前页设备' },
  { label: '实时总功率', value: formatNumber(sumOf('currentPowerKw'), 2) + ' kW', note: '设备影子聚合' }
])

function countOnline() {
  return chargerList.value.filter(row => row.onlineStatus === 'ONLINE').length
}

function sumOf(field) {
  return chargerList.value.reduce((sum, row) => {
    const value = Number(row[field])
    return sum + (Number.isFinite(value) ? value : 0)
  }, 0)
}

const dialogVisible = ref(false)
const submitLoading = ref(false)
const formRef = ref()
const form = ref(defaultForm())
const rules = {
  stationId: [{ required: true, message: '请选择归属场站', trigger: 'change' }],
  deviceNo: [
    { required: true, message: '设备编号不能为空', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_.:-]{3,64}$/, message: '仅支持字母、数字、下划线、中划线、点号和冒号，长度 3-64', trigger: 'blur' }
  ],
  name: [{ required: true, message: '设备名称不能为空', trigger: 'blur' }],
  deviceType: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  protocol: [{ required: true, message: '请选择通信协议', trigger: 'change' }],
  scene: [{ required: true, message: '请选择订单场景', trigger: 'change' }]
}

const monitorVisible = ref(false)
const monitorLoading = ref(false)
const monitorId = ref(null)
const detail = ref(null)
const selectedPort = ref(null)
const selectedPortData = computed(() => (detail.value?.ports || []).find(port => port.portNo === selectedPort.value) || null)
const preChargeHint = computed(() => {
  const meta = sceneOptions.value.find(item => item.scene === detail.value?.charger?.scene)
  const phases = meta?.preChargePhases || []
  return phases.map(preChargePhaseLabel).join(' → ')
})
const ocppSim = ref({ enabled: false, running: [] })
const ocppSimLoading = ref(false)
const ocppSimRunning = computed(() =>
  (ocppSim.value.running || []).some(item => item.identity === detail.value?.charger?.deviceNo)
)
const ocppSimVisible = computed(() =>
  ocppSim.value.enabled && detail.value?.charger?.protocol === 'OCPP16J'
)
const chargeDialogVisible = ref(false)
const chargeFormRef = ref()
const chargeForm = ref({ maxDurationMinutes: 120, chargeLimitAmount: 20 })
const chargeRules = {
  maxDurationMinutes: [{ required: true, message: '请设置最大充电时长', trigger: 'change' }],
  chargeLimitAmount: [{ required: true, message: '请设置金额上限', trigger: 'change' }]
}
const thresholdDialogVisible = ref(false)
const thresholdFormRef = ref()
const thresholdForm = ref({ overTempC: 60, overCurrentA: 10 })
const thresholdRules = {
  overTempC: [{ required: true, message: '请设置过温阈值', trigger: 'change' }],
  overCurrentA: [{ required: true, message: '请设置过流阈值', trigger: 'change' }]
}
const actionLoading = ref(false)
const history = ref([])
const historyLoading = ref(false)
const chartRef = ref(null)
let chart = null
let pollTimer = null
let pollCount = 0

function defaultForm() {
  return {
    id: null,
    stationId: null,
    deviceNo: '',
    name: '',
    groupName: '',
    deviceType: 'SOCKET_12',
    protocol: 'BALAN_MQTT',
    scene: 'TWOWHEEL',
    startMethod: 'SCAN',
    firmwareVersion: 'v1.0.0',
    ratedVoltage: 220,
    ratedPowerKw: 2.2,
    overTempC: 60,
    overCurrentA: 10,
    isSimulated: 0,
    remark: ''
  }
}

function getList() {
  loading.value = true
  listCharger(queryParams).then(res => {
    chargerList.value = res.rows || []
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

function loadGroupOptions() {
  getChargerGroups().then(res => {
    groupOptions.value = res.data || []
  })
}

function loadSceneOptions() {
  getChargerScenes().then(res => {
    sceneOptions.value = res.data || []
  })
}

function loadProtocolOptions() {
  getDeviceProtocols().then(res => {
    protocolOptions.value = res.data || []
  })
}

// 协议决定端口模型与订单时序；新建时顺手切到该协议的默认组合，编辑时不动已生成的端口
function handleProtocolChange(protocol) {
  if (form.value.id) {
    return
  }
  if (protocol === 'OCPP16J') {
    form.value.deviceType = 'DC_SINGLE'
    form.value.scene = 'DC_CAR'
    form.value.ratedVoltage = 750
    form.value.ratedPowerKw = 120
  } else {
    form.value.deviceType = 'SOCKET_12'
    form.value.scene = 'TWOWHEEL'
    form.value.ratedVoltage = 220
    form.value.ratedPowerKw = 2.2
  }
  form.value.startMethod = 'SCAN'
}

// 设备类型决定端口数与场景，口径与后端 ChargerService.validateCombination 保持一致
const deviceTypeScenes = {
  SOCKET_2: 'TWOWHEEL',
  SOCKET_10: 'TWOWHEEL',
  SOCKET_12: 'TWOWHEEL',
  DC_SINGLE: 'DC_CAR',
  DC_DUAL: 'DC_CAR',
  AC_SINGLE: 'AC_CAR',
  AC_DUAL: 'AC_CAR'
}

function handleDeviceTypeChange(deviceType) {
  if (form.value.id) {
    return
  }
  const scene = deviceTypeScenes[deviceType]
  if (!scene) {
    return
  }
  form.value.scene = scene
  if (scene === 'TWOWHEEL') {
    form.value.protocol = 'BALAN_MQTT'
    form.value.ratedVoltage = 220
    form.value.ratedPowerKw = 2.2
  } else if (scene === 'DC_CAR') {
    form.value.protocol = 'OCPP16J'
    form.value.ratedVoltage = 750
    form.value.ratedPowerKw = 120
  } else {
    form.value.protocol = 'OCPP16J'
    form.value.ratedVoltage = 220
    form.value.ratedPowerKw = 7
  }
  if (scene !== 'AC_CAR') {
    form.value.startMethod = 'SCAN'
  }
}

function handleSceneChange(scene) {
  if (scene !== 'AC_CAR') {
    form.value.startMethod = 'SCAN'
  }
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
  form.value = {
    id: row.id,
    stationId: row.stationId,
    deviceNo: row.deviceNo,
    name: row.name,
    groupName: row.groupName || '',
    deviceType: row.deviceType,
    protocol: row.protocol || 'BALAN_MQTT',
    scene: row.scene || 'TWOWHEEL',
    startMethod: row.startMethod || 'SCAN',
    firmwareVersion: row.firmwareVersion || '',
    ratedVoltage: Number(row.ratedVoltage || 220),
    ratedPowerKw: Number(row.ratedPowerKw || 2.2),
    overTempC: Number(row.overTempC || 60),
    overCurrentA: Number(row.overCurrentA || 10),
    isSimulated: row.isSimulated ?? 0,
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
    const payload = { ...form.value }
    delete payload.id
    const request = form.value.id ? updateCharger(form.value.id, payload) : addCharger(payload)
    request.then(() => {
      ElMessage.success(form.value.id ? '设备已更新' : '设备已创建，端口已自动生成')
      dialogVisible.value = false
      loadGroupOptions()
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
  ElMessageBox.confirm(`确定删除设备「${row.deviceNo}」及其 ${row.portCount} 个端口吗？`, '删除提示', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(() => delCharger(row.id)).then(() => {
    ElMessage.success('设备已删除')
    getList()
    if (monitorId.value === row.id) {
      monitorVisible.value = false
    }
  }).catch(() => {})
}

function openMonitor(row) {
  monitorId.value = row.id
  monitorVisible.value = true
  monitorLoading.value = true
  detail.value = null
  history.value = []
  selectedPort.value = null
  pollCount = 0
  loadOcppSimulator()
  getCharger(row.id).then(res => {
    detail.value = res.data
    chooseDefaultPort()
    loadHistory()
  }).finally(() => {
    monitorLoading.value = false
    startPolling()
  })
}

function chooseDefaultPort() {
  const ports = detail.value?.ports || []
  if (ports.length === 0) {
    return
  }
  const charging = ports.find(port => port.status === 'CHARGING')
  selectedPort.value = (charging || ports[0]).portNo
}

function refreshDetail() {
  if (!monitorId.value) {
    return Promise.resolve()
  }
  return getCharger(monitorId.value).then(res => {
    detail.value = res.data
    const ports = detail.value?.ports || []
    if (!ports.some(port => port.portNo === selectedPort.value)) {
      chooseDefaultPort()
      loadHistory()
    }
  })
}

function selectPort(portNo) {
  if (selectedPort.value === portNo) {
    return
  }
  selectedPort.value = portNo
  loadHistory()
}

function openStart() {
  const status = selectedPortData.value?.status
  if (!['IDLE', 'OCCUPIED'].includes(status)) {
    return
  }
  chargeForm.value = { maxDurationMinutes: 120, chargeLimitAmount: 20 }
  chargeDialogVisible.value = true
}

function submitStart() {
  chargeFormRef.value.validate(valid => {
    if (!valid) {
      return
    }
    actionLoading.value = true
    startCharging(monitorId.value, selectedPort.value, chargeForm.value).then(res => {
      ElMessage.success(`订单 ${res.data.orderNo} 已创建，等待设备回执`)
      chargeDialogVisible.value = false
      refreshDetail()
      setTimeout(refreshDetail, 1500)
      setTimeout(() => loadHistory(true), 1500)
    }).finally(() => {
      actionLoading.value = false
    })
  })
}

function handleStop() {
  ElMessageBox.confirm(`确定停止 ${detail.value?.charger?.deviceNo} 的 #${selectedPort.value} 号口充电吗？`, '停止充电', {
    type: 'warning',
    confirmButtonText: '停止',
    cancelButtonText: '取消'
  }).then(() => {
    actionLoading.value = true
    return stopCharging(monitorId.value, selectedPort.value)
  }).then(() => {
    ElMessage.success('停止指令已下发，等待设备结束订单')
    refreshDetail()
    setTimeout(refreshDetail, 1500)
    setTimeout(() => loadHistory(true), 1500)
  }).catch(() => {}).finally(() => {
    actionLoading.value = false
  })
}

function handleUnlock() {
  ElMessageBox.confirm(`确定对 #${selectedPort.value} 号口执行远程解锁吗？`, '远程解锁', {
    type: 'warning',
    confirmButtonText: '解锁',
    cancelButtonText: '取消'
  }).then(() => {
    actionLoading.value = true
    return unlockPort(monitorId.value, selectedPort.value)
  }).then(() => {
    ElMessage.success('解锁指令已下发')
    refreshDetail()
  }).catch(() => {}).finally(() => {
    actionLoading.value = false
  })
}

function handleSimulatedUnplug() {
  ElMessageBox.confirm(`确定模拟设备拔枪并结束 #${selectedPort.value} 号口占用吗？`, '模拟拔枪', {
    type: 'warning',
    confirmButtonText: '拔枪',
    cancelButtonText: '取消'
  }).then(() => {
    actionLoading.value = true
    return unplugSimulated({ deviceNo: detail.value?.charger?.deviceNo, portNo: selectedPort.value })
  }).then(() => {
    ElMessage.success('已模拟拔枪，占位费已结算')
    refreshDetail()
  }).catch(() => {}).finally(() => {
    actionLoading.value = false
  })
}

function loadOcppSimulator() {
  getOcppSimulatorStatus().then(res => {
    ocppSim.value = res.data || { enabled: false, running: [] }
  }).catch(() => {
    ocppSim.value = { enabled: false, running: [] }
  })
}

function toggleOcppSim() {
  const identity = detail.value?.charger?.deviceNo
  if (!identity) {
    return
  }
  const running = ocppSimRunning.value
  ocppSimLoading.value = true
  const action = running
    ? ElMessageBox.confirm(`确定停止模拟桩「${identity}」吗？进程会被直接结束，订单按设备离线兜底。`, '停止 OCPP 模拟', {
      type: 'warning',
      confirmButtonText: '停止',
      cancelButtonText: '取消'
    }).then(() => stopOcppSimulator(identity))
    : Promise.resolve(startOcppSimulator({
      identity,
      connectors: (detail.value?.ports || []).length || 1
    }))
  action.then(() => {
    ElMessage.success(running ? '模拟桩已停止' : '模拟桩已启动，等待 BootNotification')
    loadOcppSimulator()
    setTimeout(refreshDetail, 1500)
  }).catch(() => {}).finally(() => {
    ocppSimLoading.value = false
  })
}

function openThreshold() {
  thresholdForm.value = {
    overTempC: Number(detail.value?.charger?.overTempC || 60),
    overCurrentA: Number(detail.value?.charger?.overCurrentA || 10)
  }
  thresholdDialogVisible.value = true
}

function submitThreshold() {
  thresholdFormRef.value.validate(valid => {
    if (!valid) {
      return
    }
    actionLoading.value = true
    setPortThreshold(monitorId.value, selectedPort.value, thresholdForm.value).then(() => {
      ElMessage.success('保护阈值已下发')
      thresholdDialogVisible.value = false
      refreshDetail()
    }).finally(() => {
      actionLoading.value = false
    })
  })
}

function loadHistory(silent = false) {
  if (!monitorId.value || !selectedPort.value) {
    return
  }
  if (!silent) {
    historyLoading.value = true
  }
  getChargerHistory(monitorId.value, {
    portNo: selectedPort.value,
    minutes: 30,
    limit: 2000
  }).then(res => {
    history.value = res.data || []
    nextTick(renderChart)
  }).finally(() => {
    historyLoading.value = false
  })
}

function initChart() {
  if (!chartRef.value) {
    return
  }
  if (!chart || chart.getDom() !== chartRef.value) {
    chart?.dispose()
    chart = echarts.init(chartRef.value)
  }
}

function renderChart() {
  initChart()
  if (!chart) {
    return
  }
  const times = history.value.map(point => {
    const date = new Date(point.ts)
    return date.toLocaleTimeString('zh-CN', { hour12: false })
  })
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['功率 kW', '温度 °C'], right: 10, top: 0 },
    grid: { left: 52, right: 52, top: 42, bottom: 34 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: times,
      axisLabel: { color: '#94a3b8' },
      axisLine: { lineStyle: { color: '#e2e8f0' } }
    },
    yAxis: [
      {
        type: 'value',
        name: 'kW',
        nameTextStyle: { color: '#94a3b8' },
        splitLine: { lineStyle: { color: '#f1f5f9' } },
        axisLabel: { color: '#94a3b8' }
      },
      {
        type: 'value',
        name: '°C',
        nameTextStyle: { color: '#94a3b8' },
        splitLine: { show: false },
        axisLabel: { color: '#94a3b8' }
      }
    ],
    series: [
      {
        name: '功率 kW',
        type: 'line',
        smooth: true,
        showSymbol: false,
        data: history.value.map(point => point.powerKw),
        lineStyle: { width: 2, color: '#0ea5e9' },
        itemStyle: { color: '#0ea5e9' },
        areaStyle: { color: 'rgba(14, 165, 233, 0.10)' }
      },
      {
        name: '温度 °C',
        type: 'line',
        smooth: true,
        showSymbol: false,
        yAxisIndex: 1,
        data: history.value.map(point => point.temperature),
        lineStyle: { width: 2, color: '#f97316' },
        itemStyle: { color: '#f97316' }
      }
    ]
  }, true)
  chart.resize()
}

function handleDrawerOpened() {
  nextTick(renderChart)
}

function handleDrawerClosed() {
  stopPolling()
  chart?.dispose()
  chart = null
  monitorId.value = null
  detail.value = null
  history.value = []
  selectedPort.value = null
  ocppSim.value = { enabled: false, running: [] }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => {
    if (!monitorVisible.value) {
      return
    }
    pollCount += 1
    refreshDetail()
    if (pollCount % 6 === 0) {
      loadHistory(true)
    }
  }, 5000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  loadStationOptions()
  loadGroupOptions()
  loadSceneOptions()
  loadProtocolOptions()
  getList()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  stopPolling()
  chart?.dispose()
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped lang="scss">
.charger-page {
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

  .form-hint {
    margin-top: 4px;
    line-height: 1.5;
  }

  .device-line {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .port-tags {
    display: flex;
    justify-content: center;
    gap: 4px;
  }

  .power-value {
    color: #0f172a;
    font-weight: 600;
  }

  .temp-warning {
    color: #dc2626;
    font-weight: 600;
  }

  .monitor-header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 20px;
    width: 100%;

    h3 {
      margin: 0;
      color: #0f172a;
    }

    p {
      margin: 6px 0 0;
    }
  }

  .monitor-meta {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    color: #94a3b8;
    font-size: 12px;
  }

  .monitor-body {
    padding: 0 4px 24px;
  }

  .monitor-summary {
    margin-bottom: 18px;
  }

  .mini-stat {
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

    .time {
      font-size: 15px;
      padding-top: 4px;
    }
  }

  .section-title {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
    color: #0f172a;
    font-weight: 600;
  }

  .port-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
    gap: 10px;
    margin-bottom: 18px;
  }

  .port-toolbar {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 14px;
    margin-bottom: 10px;
    border: 1px solid #e2e8f0;
    border-radius: 10px;
    background: #f8fafc;
  }

  .port-selection {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    color: #64748b;
    font-size: 13px;

    strong {
      color: #0f172a;
      font-size: 16px;
    }
  }

  .port-order-no {
    color: #0284c7;
    font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
    font-size: 12px;
  }

  .port-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .port-order {
    margin-top: 4px;
    color: #0284c7;
    font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
    font-size: 11px;
    line-height: 1.5;
  }

  .port-user {
    color: #64748b;
    font-family: inherit;
  }

  .dialog-hint {
    margin-left: 8px;
    color: #94a3b8;
    font-size: 12px;
  }

  .port-card {
    padding: 12px 14px;
    border: 1px solid #e2e8f0;
    border-left: 4px solid #cbd5e1;
    border-radius: 10px;
    background: #fff;
    cursor: pointer;
    transition: all 0.18s ease;

    &:hover {
      border-color: #7dd3fc;
      box-shadow: 0 6px 16px rgba(15, 23, 42, 0.08);
    }

    &.charging {
      border-left-color: #10b981;
      background: linear-gradient(180deg, #f0fdf4, #fff 60%);
    }

    &.fault {
      border-left-color: #ef4444;
      background: linear-gradient(180deg, #fef2f2, #fff 60%);
    }

    &.idle {
      border-left-color: #94a3b8;
    }

    &.active {
      border-color: #0ea5e9;
      box-shadow: 0 0 0 2px rgba(14, 165, 233, 0.18);
    }
  }

  .port-top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
  }

  .port-no {
    color: #0f172a;
    font-weight: 700;
  }

  .port-power {
    color: #0f172a;
    font-size: 22px;
    font-weight: 700;

    small {
      color: #94a3b8;
      font-size: 12px;
      font-weight: 400;
    }
  }

  .port-lines {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 10px;
    margin-top: 6px;
    color: #475569;
    font-size: 12px;
  }

  .port-energy {
    margin-top: 6px;
    color: #94a3b8;
    font-size: 12px;
  }

  .chart-card {
    border: 1px solid #e2e8f0;
    border-radius: 12px;
  }

  .chart-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    color: #0f172a;
    font-weight: 600;
  }

  .chart-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .history-chart {
    width: 100%;
    height: 320px;
  }
}
</style>

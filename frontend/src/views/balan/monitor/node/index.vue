<template>
  <div class="app-container node-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">MONITOR · NODE STATUS</p>
        <h2>节点状态</h2>
        <p class="page-desc">
          本实例对 MySQL、Redis、EMQX、TDengine 的实时探测，以及当前 JVM 的内存、线程与 CPU。
          四项独立计时，单项超时不会拖住整页；凭据不会出现在响应或页面里。
        </p>
      </div>
      <div class="page-actions">
        <el-switch v-model="autoRefresh" active-text="30 秒自动刷新" @change="toggleTimer" />
        <el-button icon="Refresh" :loading="loading" @click="load">重新检测</el-button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" class="tip" />

    <section class="instance-band">
      <div class="band-main">
        <p class="band-k">CURRENT INSTANCE</p>
        <div class="band-id">{{ instance.instanceId || '-' }}</div>
        <p class="band-sub">
          <el-tag size="small" effect="plain" :type="editionType">{{ editionLabel }}</el-tag>
          <span>{{ instance.host || '-' }} · PID {{ instance.pid || '-' }}</span>
          <span>启动于 {{ formatTime(instance.startedAt) }}</span>
        </p>
      </div>
      <div class="band-stats">
        <div class="band-stat">
          <span>运行时长</span>
          <strong>{{ uptime(instance.uptimeSeconds) }}</strong>
        </div>
        <div class="band-stat">
          <span>Profile</span>
          <strong>{{ profileText }}</strong>
        </div>
        <div class="band-stat">
          <span>检测时间</span>
          <strong>{{ formatTime(data.generatedAt) }}</strong>
        </div>
      </div>
    </section>

    <div class="section-title">中间件连通性</div>
    <div v-loading="loading" class="mw-grid">
      <article v-for="item in middlewares" :key="item.key" class="mw-card" :class="statusClass(item)">
        <div class="mw-head">
          <span class="status-dot" />
          <span class="mw-name">{{ item.name }}</span>
          <el-tag size="small" effect="plain" :type="tagType(item)">{{ tagText(item) }}</el-tag>
        </div>
        <div class="mw-target" :title="item.target">{{ item.target || '-' }}</div>
        <div v-if="item.up" class="mw-detail">{{ item.detail || '连接正常' }}</div>
        <div v-else-if="item.enabled" class="mw-error">{{ item.error || '探测失败' }}</div>
        <div v-else class="mw-detail muted">配置中已关闭，未发起探测</div>
        <div class="mw-foot">
          <span v-if="item.latencyMs !== null && item.latencyMs !== undefined">耗时 {{ item.latencyMs }} ms</span>
          <span v-else>未探测</span>
        </div>
      </article>
    </div>

    <div class="section-title">JVM 与运行时</div>
    <div class="kpi-row">
      <article class="kpi">
        <div class="kpi-label">堆内存</div>
        <div class="kpi-value">
          {{ number(jvm.heapUsedMb) }}<small>/ {{ jvm.heapMaxMb ? number(jvm.heapMaxMb) : '—' }} MB</small>
        </div>
        <el-progress
          class="heap-bar"
          :percentage="heapPercent"
          :stroke-width="7"
          :show-text="false"
          :status="heapStatus"
        />
        <div class="kpi-note">已提交 {{ number(jvm.heapCommittedMb) }} MB · 非堆 {{ number(jvm.nonHeapUsedMb) }} MB</div>
      </article>
      <article class="kpi" :class="{ warm: cpuPercent >= 70 }">
        <div class="kpi-label">进程 CPU</div>
        <div class="kpi-value">
          {{ percent(jvm.processCpuPercent) }}
          <small v-if="jvm.processCpuPercent !== null && jvm.processCpuPercent !== undefined">%</small>
        </div>
        <div class="kpi-note">系统 CPU {{ percent(jvm.systemCpuPercent) }}% · 负载 {{ jvm.systemLoadAverage ?? '—' }}</div>
      </article>
      <article class="kpi blue">
        <div class="kpi-label">线程</div>
        <div class="kpi-value">{{ jvm.threadCount ?? '-' }}<small>个</small></div>
        <div class="kpi-note">峰值 {{ jvm.peakThreadCount ?? '-' }} · 守护 {{ jvm.daemonThreadCount ?? '-' }}</div>
      </article>
      <article class="kpi gray">
        <div class="kpi-label">GC</div>
        <div class="kpi-value">{{ jvm.gcCount ?? '-' }}<small>次</small></div>
        <div class="kpi-note">累计 {{ number((jvm.gcTimeMs || 0) / 1000) }} 秒</div>
      </article>
    </div>

    <el-card shadow="never" class="detail-card">
      <template #header>
        <span class="card-title">运行时明细</span>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="Java">{{ jvm.javaVersion || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ jvm.javaVendor || '-' }}</el-descriptions-item>
        <el-descriptions-item label="操作系统">{{ jvm.osName || '-' }} / {{ jvm.osArch || '-' }}</el-descriptions-item>
        <el-descriptions-item label="CPU 核数">{{ jvm.availableProcessors ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="JVM 启动时间">{{ formatTime(jvm.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="JVM 运行时长">{{ uptime(jvm.uptimeSeconds) }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { getNodeStatus } from '@/api/balan/monitor'
import { formatTime } from '@/utils/balan'

defineOptions({ name: 'BalanMonitorNode' })

const REFRESH_SECONDS = 30

const loading = ref(false)
const error = ref('')
const autoRefresh = ref(true)
const data = ref({})
let timer = null

const instance = computed(() => data.value.instance || {})
const middlewares = computed(() => data.value.middlewares || [])
const jvm = computed(() => data.value.jvm || {})
const profileText = computed(() => {
  const profiles = instance.value.profiles || []
  return Array.isArray(profiles) && profiles.length ? profiles.join(', ') : '-'
})
const editionLabel = computed(() => (instance.value.edition === 'standard' ? '标准版' : '社区版'))
const editionType = computed(() => (instance.value.edition === 'standard' ? 'warning' : 'success'))
const heapPercent = computed(() => {
  const value = Number(jvm.value.heapUsedPercent)
  if (Number.isNaN(value)) {
    return 0
  }
  return Math.min(Math.max(value, 0), 100)
})
const heapStatus = computed(() => {
  if (heapPercent.value >= 90) {
    return 'exception'
  }
  return heapPercent.value >= 75 ? 'warning' : 'success'
})
const cpuPercent = computed(() => Number(jvm.value.processCpuPercent) || 0)

function tagType(item) {
  if (!item.enabled) {
    return 'info'
  }
  return item.up ? 'success' : 'danger'
}

function tagText(item) {
  if (!item.enabled) {
    return '未启用'
  }
  return item.up ? '正常' : '异常'
}

function statusClass(item) {
  if (!item.enabled) {
    return 'is-off'
  }
  return item.up ? 'is-up' : 'is-down'
}

function number(value) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return Number(value).toFixed(1)
}

function percent(value) {
  if (value === null || value === undefined) {
    return '—'
  }
  return Number(value).toFixed(1)
}

function uptime(seconds) {
  if (seconds === null || seconds === undefined) {
    return '-'
  }
  const total = Math.max(0, Number(seconds))
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (days > 0) {
    return `${days} 天 ${hours} 小时`
  }
  if (hours > 0) {
    return `${hours} 小时 ${minutes} 分`
  }
  return `${minutes} 分`
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res = await getNodeStatus()
    data.value = res.data || {}
  } catch (e) {
    error.value = e?.message || '节点状态检测失败'
  } finally {
    loading.value = false
  }
}

function toggleTimer(enabled) {
  stopTimer()
  if (enabled) {
    timer = setInterval(() => {
      if (!document.hidden) {
        load()
      }
    }, REFRESH_SECONDS * 1000)
  }
}

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onMounted(() => {
  load()
  toggleTimer(autoRefresh.value)
})

onUnmounted(stopTimer)
</script>

<style scoped lang="scss">
.node-page {
  .page-head {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    margin-bottom: 14px;
    gap: 12px;
    flex-wrap: wrap;
  }

  .page-kicker {
    margin: 0 0 4px;
    font-size: 12px;
    letter-spacing: 1px;
    color: var(--el-color-primary);
  }

  .page-head h2 {
    margin: 0 0 4px;
    font-size: 20px;
  }

  .page-desc {
    margin: 0;
    max-width: 820px;
    font-size: 13px;
    line-height: 1.7;
    color: var(--el-text-color-secondary);
  }

  .page-actions {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  .tip {
    margin-bottom: 14px;
  }

  .instance-band {
    display: grid;
    grid-template-columns: minmax(260px, 1fr) auto;
    align-items: center;
    gap: 24px;
    padding: 20px 26px;
    border-radius: 16px;
    color: #eaf6f1;
    background:
      radial-gradient(circle at 84% 16%, rgba(255, 196, 92, 0.24), transparent 46%),
      linear-gradient(112deg, #10241f 0%, #145343 56%, #1b7a5c 100%);
    box-shadow: 0 18px 38px rgba(6, 60, 42, 0.2);
  }

  .band-k {
    margin: 0 0 8px;
    font-size: 12px;
    letter-spacing: 2.4px;
    color: #9fe8cd;
  }

  .band-id {
    font-size: 26px;
    font-weight: 700;
    line-height: 1.2;
    word-break: break-all;
  }

  .band-sub {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
    margin: 10px 0 0;
    font-size: 13px;
    color: #b6e9d5;
  }

  .band-stats {
    display: grid;
    grid-template-columns: repeat(3, minmax(110px, auto));
    gap: 10px;
  }

  .band-stat {
    display: flex;
    flex-direction: column;
    gap: 5px;
    padding: 12px 14px;
    border: 1px solid rgba(190, 245, 220, 0.22);
    border-radius: 12px;
    background: rgba(4, 40, 28, 0.22);

    span {
      font-size: 12px;
      color: #9adfc4;
    }

    strong {
      font-size: 15px;
      font-weight: 600;
    }
  }

  .section-title {
    margin: 18px 0 10px;
    font-size: 14px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .mw-grid {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
    min-height: 130px;
  }

  .mw-card {
    padding: 16px;
    border: 1px solid var(--bc-border, var(--el-border-color-lighter));
    border-left: 3px solid var(--el-color-info);
    border-radius: 12px;
    background: var(--bc-surface, #fff);
    box-shadow: 0 4px 14px rgba(11, 31, 26, 0.04);

    &.is-up {
      border-left-color: var(--el-color-success);
    }

    &.is-down {
      border-left-color: var(--el-color-danger);
      background: #fffafa;
    }

    &.is-off {
      opacity: 0.72;
    }
  }

  .mw-head {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .status-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--el-color-info);
  }

  .is-up .status-dot {
    background: var(--el-color-success);
    box-shadow: 0 0 0 4px rgba(103, 194, 58, 0.16);
  }

  .is-down .status-dot {
    background: var(--el-color-danger);
    box-shadow: 0 0 0 4px rgba(245, 108, 108, 0.16);
  }

  .mw-name {
    flex: 1;
    font-weight: 600;
  }

  .mw-target {
    margin: 12px 0 8px;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mw-detail {
    min-height: 34px;
    font-size: 13px;
    line-height: 1.6;
    word-break: break-all;

    &.muted {
      color: var(--el-text-color-secondary);
    }
  }

  .mw-error {
    min-height: 34px;
    font-size: 12px;
    line-height: 1.6;
    color: var(--el-color-danger);
    word-break: break-all;
  }

  .mw-foot {
    margin-top: 12px;
    font-size: 12px;
    color: #93a49c;
  }

  .kpi-row {
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 12px;
  }

  .kpi {
    padding: 14px 16px;
    border: 1px solid var(--bc-border, var(--el-border-color-lighter));
    border-left: 3px solid var(--el-color-primary);
    border-radius: 12px;
    background: var(--bc-surface, #fff);
    box-shadow: 0 4px 14px rgba(11, 31, 26, 0.04);

    &.blue {
      border-left-color: #528bfd;
    }

    &.gray {
      border-left-color: #6b7d75;
    }

    &.warm {
      border-left-color: #f79009;
    }
  }

  .kpi-label {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .kpi-value {
    margin: 7px 0 5px;
    font-size: 23px;
    font-weight: 700;
    line-height: 1.1;

    small {
      margin-left: 4px;
      font-size: 12px;
      font-weight: 400;
      color: #93a49c;
    }
  }

  .kpi-note {
    margin-top: 6px;
    font-size: 12px;
    color: #93a49c;
  }

  .heap-bar {
    margin-top: 8px;
  }

  .detail-card {
    margin-top: 14px;
    border-radius: 10px;
  }

  .card-title {
    font-weight: 600;
  }

  @media (max-width: 1200px) {
    .instance-band {
      grid-template-columns: 1fr;
    }

    .mw-grid,
    .kpi-row {
      grid-template-columns: repeat(2, minmax(0, 1fr));
    }
  }

  @media (max-width: 768px) {
    .band-stats,
    .mw-grid,
    .kpi-row {
      grid-template-columns: 1fr;
    }
  }
}
</style>

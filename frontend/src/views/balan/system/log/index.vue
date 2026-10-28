<template>
  <div class="app-container log-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">SYSTEM · AUDIT</p>
        <h2>审计日志</h2>
        <p class="page-desc">记录关键写操作与登录行为，可追溯操作人、IP、接口、参数、结果与耗时。</p>
      </div>
      <div class="page-actions">
        <el-button icon="Refresh" @click="activeTab === 'oper' ? getOperList() : getLoginList()">刷新</el-button>
        <el-button v-hasPermi="['system:log:export']" type="primary" plain icon="Download" @click="handleExport">
          导出操作日志
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="log-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="操作日志" name="oper">
        <el-card shadow="never" class="table-card">
          <el-form :model="operQuery" :inline="true" label-width="72px">
            <el-form-item label="模块">
              <el-select v-model="operQuery.title" placeholder="全部模块" clearable style="width: 170px">
                <el-option v-for="item in titleOptions" :key="item" :label="item" :value="item" />
              </el-select>
            </el-form-item>
            <el-form-item label="操作人">
              <el-input v-model="operQuery.operName" placeholder="登录名" clearable style="width: 150px" @keyup.enter="handleOperQuery" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="operQuery.businessType" placeholder="全部类型" clearable style="width: 130px">
                <el-option v-for="item in businessTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="结果">
              <el-select v-model="operQuery.status" placeholder="全部结果" clearable style="width: 120px">
                <el-option label="成功" :value="0" />
                <el-option label="失败" :value="1" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="operQuery.keyword" placeholder="接口 / IP / 方法" clearable style="width: 220px" @keyup.enter="handleOperQuery" />
            </el-form-item>
            <el-form-item label="时间">
              <el-date-picker
                v-model="operRange"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                style="width: 340px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleOperQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetOperQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-row :gutter="10" class="toolbar">
            <el-col :span="1.5">
              <el-tag type="info" effect="plain">共 {{ operTotal }} 条</el-tag>
            </el-col>
            <el-col :span="1.5">
              <el-tag type="warning" effect="plain">密码字段已自动脱敏，参数超 2000 字符截断</el-tag>
            </el-col>
          </el-row>

          <el-table v-loading="operLoading" :data="operList" row-key="operId">
            <el-table-column prop="operId" label="日志号" width="90" />
            <el-table-column prop="title" label="模块" width="120" />
            <el-table-column label="类型" width="90" align="center">
              <template #default="{ row }">{{ businessTypeLabel(row.businessType) }}</template>
            </el-table-column>
            <el-table-column prop="operName" label="操作人" width="120" />
            <el-table-column label="接口" min-width="260">
              <template #default="{ row }">
                <div>{{ row.operUrl }}</div>
                <div class="muted">{{ row.requestMethod }} · {{ row.method }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="operIp" label="来源 IP" width="140" />
            <el-table-column label="操作时间" width="170">
              <template #default="{ row }">{{ formatTime(row.operTime) }}</template>
            </el-table-column>
            <el-table-column label="耗时" width="90" align="right">
              <template #default="{ row }">{{ row.costTime || 0 }} ms</template>
            </el-table-column>
            <el-table-column label="结果" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small" effect="plain">
                  {{ row.status === 0 ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-hasPermi="['system:log:query']" link type="primary" icon="View" @click="openDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <pagination
            v-show="operTotal > 0"
            v-model:page="operQuery.pageNum"
            v-model:limit="operQuery.pageSize"
            :total="operTotal"
            @pagination="getOperList"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="登录日志" name="login">
        <el-card shadow="never" class="table-card">
          <el-form :model="loginQuery" :inline="true" label-width="72px">
            <el-form-item label="用户名">
              <el-input v-model="loginQuery.userName" placeholder="登录名" clearable style="width: 160px" @keyup.enter="handleLoginQuery" />
            </el-form-item>
            <el-form-item label="结果">
              <el-select v-model="loginQuery.status" placeholder="全部结果" clearable style="width: 120px">
                <el-option label="成功" value="0" />
                <el-option label="失败" value="1" />
              </el-select>
            </el-form-item>
            <el-form-item label="关键词">
              <el-input v-model="loginQuery.keyword" placeholder="IP / 描述" clearable style="width: 200px" @keyup.enter="handleLoginQuery" />
            </el-form-item>
            <el-form-item label="时间">
              <el-date-picker
                v-model="loginRange"
                type="datetimerange"
                value-format="YYYY-MM-DD HH:mm:ss"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                style="width: 340px"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleLoginQuery">搜索</el-button>
              <el-button icon="Refresh" @click="resetLoginQuery">重置</el-button>
            </el-form-item>
          </el-form>

          <el-table v-loading="loginLoading" :data="loginList" row-key="infoId">
            <el-table-column prop="infoId" label="编号" width="90" />
            <el-table-column prop="userName" label="用户名" width="140" />
            <el-table-column prop="ipaddr" label="登录 IP" width="150" />
            <el-table-column prop="loginLocation" label="登录地点" width="120" />
            <el-table-column prop="browser" label="浏览器" min-width="160" />
            <el-table-column prop="os" label="操作系统" min-width="140" />
            <el-table-column label="结果" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="loginStatusType(row.status)" size="small" effect="plain">{{ loginStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="msg" label="描述" min-width="150" show-overflow-tooltip />
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ formatTime(row.loginTime) }}</template>
            </el-table-column>
          </el-table>

          <pagination
            v-show="loginTotal > 0"
            v-model:page="loginQuery.pageNum"
            v-model:limit="loginQuery.pageSize"
            :total="loginTotal"
            @pagination="getLoginList"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="detailVisible" title="审计详情" size="52%">
      <div v-if="detail" v-loading="detailLoading" class="detail-body">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="日志号">{{ detail.operId }}</el-descriptions-item>
          <el-descriptions-item label="模块">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="业务类型">{{ businessTypeLabel(detail.businessType) }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ detail.operName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="请求方式">{{ detail.requestMethod }}</el-descriptions-item>
          <el-descriptions-item label="来源 IP">{{ detail.operIp }}</el-descriptions-item>
          <el-descriptions-item label="操作时间">{{ formatTime(detail.operTime) }}</el-descriptions-item>
          <el-descriptions-item label="耗时">{{ detail.costTime || 0 }} ms</el-descriptions-item>
          <el-descriptions-item label="接口" :span="2">{{ detail.operUrl }}</el-descriptions-item>
          <el-descriptions-item label="目标方法" :span="2">{{ detail.method }}</el-descriptions-item>
          <el-descriptions-item label="结果" :span="2">
            <el-tag :type="detail.status === 0 ? 'success' : 'danger'" size="small" effect="plain">
              {{ detail.status === 0 ? '成功' : '失败' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title"><span>请求参数</span></div>
        <pre class="code-block">{{ prettyJson(detail.operParam) }}</pre>

        <div class="section-title"><span>返回结果</span></div>
        <pre class="code-block">{{ prettyJson(detail.jsonResult) }}</pre>

        <template v-if="detail.errorMsg">
          <div class="section-title"><span>错误信息</span></div>
          <pre class="code-block error">{{ detail.errorMsg }}</pre>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { getCurrentInstance, onMounted, reactive, ref } from 'vue'
import { getOperLog, listLoginLog, listOperLog, listOperLogTitles } from '@/api/balan/system'
import { businessTypeLabel, businessTypeOptions, formatTime, loginStatusLabel, loginStatusType } from '@/utils/balan'

defineOptions({ name: 'BalanSystemLog' })

const { proxy } = getCurrentInstance()

const activeTab = ref('oper')
const operLoading = ref(false)
const loginLoading = ref(false)
const detailLoading = ref(false)
const detailVisible = ref(false)
const operList = ref([])
const loginList = ref([])
const operTotal = ref(0)
const loginTotal = ref(0)
const titleOptions = ref([])
const detail = ref(null)
const operRange = ref([])
const loginRange = ref([])

const operQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  title: undefined,
  operName: undefined,
  businessType: undefined,
  status: undefined,
  keyword: undefined
})

const loginQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  userName: undefined,
  status: undefined,
  keyword: undefined
})

function operParams() {
  return {
    ...operQuery,
    beginTime: operRange.value?.[0],
    endTime: operRange.value?.[1]
  }
}

async function getOperList() {
  operLoading.value = true
  try {
    const res = await listOperLog(operParams())
    operList.value = res.rows || []
    operTotal.value = res.total || 0
  } finally {
    operLoading.value = false
  }
}

function handleOperQuery() {
  operQuery.pageNum = 1
  getOperList()
}

function resetOperQuery() {
  Object.assign(operQuery, { pageNum: 1, title: undefined, operName: undefined, businessType: undefined, status: undefined, keyword: undefined })
  operRange.value = []
  getOperList()
}

async function getLoginList() {
  loginLoading.value = true
  try {
    const res = await listLoginLog({
      ...loginQuery,
      beginTime: loginRange.value?.[0],
      endTime: loginRange.value?.[1]
    })
    loginList.value = res.rows || []
    loginTotal.value = res.total || 0
  } finally {
    loginLoading.value = false
  }
}

function handleLoginQuery() {
  loginQuery.pageNum = 1
  getLoginList()
}

function resetLoginQuery() {
  Object.assign(loginQuery, { pageNum: 1, userName: undefined, status: undefined, keyword: undefined })
  loginRange.value = []
  getLoginList()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getOperLog(row.operId)
    detail.value = res.data
  } finally {
    detailLoading.value = false
  }
}

function prettyJson(value) {
  if (!value) return '无'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch (error) {
    return value
  }
}

function handleTabChange(name) {
  if (name === 'login') {
    getLoginList()
  } else {
    getOperList()
  }
}

function handleExport() {
  proxy.download('/api/v1/system/logs/oper/export', operParams(), `审计日志_${new Date().getTime()}.xlsx`)
}

onMounted(async () => {
  const res = await listOperLogTitles()
  titleOptions.value = res.data || []
  getOperList()
})
</script>

<style scoped lang="scss">
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
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.page-actions {
  display: flex;
  gap: 8px;
}

.log-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }
}

.table-card {
  border-radius: 10px;
}

.toolbar {
  margin-bottom: 10px;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 16px 0 8px;
  font-weight: 600;
}

.code-block {
  margin: 0;
  padding: 12px;
  max-height: 260px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.6;
  background: var(--el-fill-color-light);
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-all;

  &.error {
    background: var(--el-color-danger-light-9);
    color: var(--el-color-danger);
  }
}
</style>

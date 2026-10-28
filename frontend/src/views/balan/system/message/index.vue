<template>
  <div class="app-container message-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">MESSAGES · INBOX</p>
        <h2>消息中心</h2>
        <p class="page-desc">告警与订单事件在这里集中留痕，未读消息在顶栏红点提示；已读状态按账号独立记录。</p>
      </div>
      <div class="page-actions">
        <el-button icon="Refresh" @click="getList">刷新</el-button>
        <el-button type="primary" plain icon="Check" :disabled="unread === 0" @click="handleReadAll">全部已读</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-form :model="queryParams" :inline="true" label-width="68px" class="filter-form">
        <el-form-item label="消息类型">
          <el-select v-model="queryParams.msgType" placeholder="全部类型" clearable style="width: 150px">
            <el-option v-for="item in typeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="queryParams.severity" placeholder="全部级别" clearable style="width: 140px">
            <el-option v-for="item in severityOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="toolbar">
        <div class="toolbar-left">
          <el-tag type="info" effect="plain">共 {{ total }} 条</el-tag>
          <el-tag v-if="unread > 0" type="danger" effect="plain">未读 {{ unread > 99 ? '99+' : unread }}</el-tag>
          <el-tag v-else type="success" effect="plain">全部已读</el-tag>
        </div>
      </div>

      <el-table v-loading="loading" :data="messageList" row-key="id" :row-class-name="rowClassName">
        <el-table-column label="状态" width="82" align="center">
          <template #default="{ row }">
            <el-tag v-if="!row.read" size="small" type="danger" effect="dark">未读</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">已读</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTagType(row.msgType)" effect="plain">{{ typeLabel(row.msgType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="86" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="severityTagType(row.severity)" effect="plain">{{ severityLabel(row.severity) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="消息" min-width="360">
          <template #default="{ row }">
            <div class="msg-title">{{ row.title }}</div>
            <div class="muted">{{ row.content || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="165">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="canOpen(row)" link type="primary" icon="View" @click="openBiz(row)">查看</el-button>
            <el-button v-if="!row.read" link type="success" icon="Check" @click="handleRead(row)">标记已读</el-button>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listMessages,
  markAllMessagesRead,
  markMessageRead
} from '@/api/balan/message'
import { formatTime } from '@/utils/balan'
import useMessageStore from '@/store/modules/message'

defineOptions({ name: 'BalanMessage' })

const router = useRouter()
const messageStore = useMessageStore()

const loading = ref(false)
const messageList = ref([])
const total = ref(0)
const unread = computed(() => messageStore.unread)

const typeOptions = [
  { value: 'ALERT', label: '告警' },
  { value: 'ORDER', label: '订单' },
  { value: 'SYSTEM', label: '系统' }
]
const severityOptions = [
  { value: 'CRITICAL', label: '严重' },
  { value: 'MAJOR', label: '重要' },
  { value: 'INFO', label: '提示' }
]

const queryParams = reactive({
  pageNum: 1,
  pageSize: 15,
  msgType: undefined,
  severity: undefined
})

function getList() {
  loading.value = true
  listMessages(queryParams).then(res => {
    messageList.value = res.rows || []
    total.value = res.total || 0
  }).finally(() => {
    loading.value = false
  })
  loadUnread()
}

function loadUnread() {
  messageStore.refresh()
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.msgType = undefined
  queryParams.severity = undefined
  queryParams.pageNum = 1
  getList()
}

function handleRead(row) {
  markMessageRead(row.id).then(() => {
    row.read = true
    ElMessage.success('已标记为已读')
    loadUnread()
  })
}

function handleReadAll() {
  ElMessageBox.confirm('确认将当前可见的全部消息标记为已读吗？', '全部已读', {
    type: 'info',
    confirmButtonText: '确认',
    cancelButtonText: '取消'
  }).then(() => markAllMessagesRead()).then(() => {
    ElMessage.success('已全部标记为已读')
    getList()
  }).catch(() => {})
}

function canOpen(row) {
  return row.bizType === 'ALERT' || row.bizType === 'ORDER'
}

function openBiz(row) {
  if (!row.read) {
    markMessageRead(row.id).then(() => loadUnread()).catch(() => {})
  }
  if (row.bizType === 'ALERT') {
    router.push('/alert/record')
  } else if (row.bizType === 'ORDER') {
    router.push('/order/list')
  }
}

function rowClassName({ row }) {
  return row.read ? '' : 'unread-row'
}

function typeLabel(type) {
  return typeOptions.find(item => item.value === type)?.label || type || '-'
}

function typeTagType(type) {
  return { ALERT: 'danger', ORDER: 'primary', SYSTEM: 'info' }[type] || 'info'
}

function severityLabel(severity) {
  return severityOptions.find(item => item.value === severity)?.label || severity || '-'
}

function severityTagType(severity) {
  return { CRITICAL: 'danger', MAJOR: 'warning', INFO: 'info' }[severity] || 'info'
}

onMounted(getList)
</script>

<style scoped lang="scss">
.message-page {
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

  .filter-form {
    margin-bottom: 4px;
  }

  .toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 10px;
  }

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .msg-title {
    color: #0f172a;
    font-weight: 600;
  }

  .muted {
    margin-top: 3px;
    color: #94a3b8;
    font-size: 12px;
    line-height: 1.5;
  }

  :deep(.unread-row) {
    background: #f8fbff;

    td {
      background: transparent !important;
    }
  }
}
</style>

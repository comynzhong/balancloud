<template>
  <div class="app-container blacklist-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">OPERATIONS · BLACKLIST</p>
        <h2>黑名单管理</h2>
        <p class="page-desc">按小程序用户或车牌号拉黑，命中后无法在小程序发起充电；信用分与风控策略由标准版提供。</p>
      </div>
      <div class="page-actions">
        <el-button type="primary" icon="Plus" v-hasPermi="['blacklist:add']" @click="openCreate">新增拉黑</el-button>
        <el-button icon="Refresh" @click="getList">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="72px">
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="queryParams.keyword" placeholder="昵称 / 手机号 / 车牌" clearable style="width: 220px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="拉黑维度" prop="scopeType">
          <el-select v-model="queryParams.scopeType" placeholder="全部维度" clearable style="width: 130px">
            <el-option label="用户" value="USER" />
            <el-option label="车牌" value="PLATE" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
            <el-option label="生效中" value="0" />
            <el-option label="已解除" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="toolbar">
        <el-col :span="1.5">
          <el-tag type="info" effect="plain">共 {{ total }} 条记录</el-tag>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="list">
        <el-table-column label="拉黑对象" min-width="230">
          <template #default="{ row }">
            <div class="object-cell">
              <el-tag size="small" :type="row.scopeType === 'PLATE' ? 'warning' : 'primary'" effect="plain">
                {{ row.scopeType === 'PLATE' ? '车牌' : '用户' }}
              </el-tag>
              <div>
                <div class="strong">
                  {{ row.scopeType === 'PLATE' ? row.plateNo || '-' : row.nickname || '未设置昵称' }}
                </div>
                <div v-if="row.scopeType !== 'PLATE'" class="muted">{{ row.phone || '无手机号' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="拉黑原因" min-width="220" prop="reason" show-overflow-tooltip />
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row)" effect="plain">{{ statusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="到期时间" min-width="165">
          <template #default="{ row }">{{ row.expireAt ? formatTime(row.expireAt) : '长期有效' }}</template>
        </el-table-column>
        <el-table-column label="创建" min-width="180">
          <template #default="{ row }">
            <div>{{ row.createBy || '-' }}</div>
            <div class="muted">{{ formatTime(row.createTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === '0'"
              v-hasPermi="['blacklist:edit']"
              link
              type="warning"
              icon="CircleClose"
              @click="handleRelease(row)"
            >解除</el-button>
            <el-button v-hasPermi="['blacklist:edit']" link type="primary" icon="Edit" @click="openEdit(row)">编辑</el-button>
            <el-button v-hasPermi="['blacklist:remove']" link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无黑名单记录" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑黑名单' : '新增拉黑'" width="520px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <template v-if="!isEdit">
          <el-form-item label="拉黑维度" prop="scopeType">
            <el-radio-group v-model="form.scopeType" @change="handleScopeChange">
              <el-radio-button label="USER">用户</el-radio-button>
              <el-radio-button label="PLATE">车牌</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="form.scopeType === 'USER'" label="小程序用户" prop="appUserId">
            <el-select
              v-model="form.appUserId"
              filterable
              remote
              clearable
              reserve-keyword
              placeholder="输入昵称或手机号搜索"
              :remote-method="searchUsers"
              :loading="userLoading"
              style="width: 100%"
            >
              <el-option
                v-for="item in userOptions"
                :key="item.id"
                :label="`${item.nickname || '未设置昵称'} · ${item.phone || '无手机号'}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-else label="车牌号" prop="plateNo">
            <el-input v-model="form.plateNo" placeholder="例如 粤B12345" maxlength="20" style="width: 100%" />
          </el-form-item>
        </template>
        <el-form-item v-else label="拉黑对象">
          <span class="strong">
            {{ form.scopeType === 'PLATE' ? form.plateNo : `${form.nickname || '-'} · ${form.phone || '无手机号'}` }}
          </span>
        </el-form-item>
        <el-form-item label="拉黑原因" prop="reason">
          <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="请填写拉黑原因" />
        </el-form-item>
        <el-form-item label="到期时间" prop="expireAt">
          <el-date-picker
            v-model="form.expireAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="留空表示长期有效"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addBlacklist,
  delBlacklist,
  listBlacklist,
  listBlacklistUserOptions,
  updateBlacklist
} from '@/api/balan/blacklist'
import { formatTime } from '@/utils/balan'

defineOptions({ name: 'BalanBlacklist' })

const loading = ref(false)
const showSearch = ref(true)
const total = ref(0)
const list = ref([])
const queryRef = ref()
const queryParams = reactive({
  keyword: undefined,
  scopeType: undefined,
  status: undefined,
  pageNum: 1,
  pageSize: 10
})

const dialogVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const formRef = ref()
const userOptions = ref([])
const userLoading = ref(false)

const form = reactive({
  id: undefined,
  scopeType: 'USER',
  appUserId: undefined,
  nickname: undefined,
  phone: undefined,
  plateNo: undefined,
  reason: undefined,
  expireAt: undefined
})

const rules = {
  scopeType: [{ required: true, message: '请选择拉黑维度', trigger: 'change' }],
  appUserId: [{ required: true, message: '请选择小程序用户', trigger: 'change' }],
  plateNo: [
    { required: true, message: '请填写车牌号', trigger: 'blur' },
    { pattern: /^[A-Z0-9\u4e00-\u9fa5]{4,12}$/, message: '车牌号格式不正确', trigger: 'blur' }
  ],
  reason: [{ required: true, message: '请填写拉黑原因', trigger: 'blur' }]
}

function getList() {
  loading.value = true
  listBlacklist(queryParams)
    .then(res => {
      list.value = res.rows || []
      total.value = res.total || 0
    })
    .finally(() => {
      loading.value = false
    })
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.keyword = undefined
  queryParams.scopeType = undefined
  queryParams.status = undefined
  queryRef.value?.resetFields()
  handleQuery()
}

function resetForm() {
  form.id = undefined
  form.scopeType = 'USER'
  form.appUserId = undefined
  form.nickname = undefined
  form.phone = undefined
  form.plateNo = undefined
  form.reason = undefined
  form.expireAt = undefined
  formRef.value?.clearValidate()
}

function openCreate() {
  isEdit.value = false
  resetForm()
  searchUsers('')
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  form.id = row.id
  form.scopeType = row.scopeType
  form.appUserId = row.appUserId
  form.nickname = row.nickname
  form.phone = row.phone
  form.plateNo = row.plateNo
  form.reason = row.reason
  form.expireAt = row.expireAt || undefined
  formRef.value?.clearValidate()
  dialogVisible.value = true
}

function handleScopeChange() {
  form.appUserId = undefined
  form.plateNo = undefined
  formRef.value?.clearValidate(['appUserId', 'plateNo'])
}

function searchUsers(keyword) {
  userLoading.value = true
  listBlacklistUserOptions(keyword)
    .then(res => {
      userOptions.value = res.data || []
    })
    .finally(() => {
      userLoading.value = false
    })
}

function submitForm() {
  formRef.value.validate(valid => {
    if (!valid) return
    submitting.value = true
    const done = () => {
      submitting.value = false
      dialogVisible.value = false
      ElMessage.success(isEdit.value ? '已更新' : '已加入黑名单')
      getList()
    }
    const fail = () => {
      submitting.value = false
    }
    if (isEdit.value) {
      updateBlacklist(form.id, { reason: form.reason, expireAt: form.expireAt || null })
        .then(done)
        .catch(fail)
      return
    }
    const payload = {
      scopeType: form.scopeType,
      appUserId: form.scopeType === 'USER' ? form.appUserId : undefined,
      plateNo: form.scopeType === 'PLATE' ? form.plateNo : undefined,
      reason: form.reason,
      expireAt: form.expireAt || null
    }
    addBlacklist(payload)
      .then(done)
      .catch(fail)
  })
}

function handleRelease(row) {
  ElMessageBox.confirm('解除后该对象可以重新发起充电，确认解除？', '解除黑名单', { type: 'warning' })
    .then(() => updateBlacklist(row.id, { status: '1' }))
    .then(() => {
      ElMessage.success('已解除')
      getList()
    })
    .catch(() => {})
}

function handleDelete(row) {
  ElMessageBox.confirm('删除后不可恢复，确认删除该条黑名单？', '删除黑名单', { type: 'warning' })
    .then(() => delBlacklist(row.id))
    .then(() => {
      ElMessage.success('已删除')
      getList()
    })
    .catch(() => {})
}

function statusLabel(row) {
  if (row.status === '1') return '已解除'
  if (row.expired) return '已过期'
  return '生效中'
}

function statusType(row) {
  if (row.status === '1') return 'info'
  if (row.expired) return 'warning'
  return 'danger'
}

onMounted(getList)
</script>

<style scoped lang="scss">
.blacklist-page {
  .page-head {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 16px;
    margin-bottom: 18px;
  }

  .page-kicker {
    margin: 0 0 6px;
    color: #16a34a;
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
    margin-bottom: 10px;
  }

  .object-cell {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .strong {
    color: #0f172a;
    font-weight: 600;
  }

  .muted {
    color: #94a3b8;
    font-size: 12px;
  }
}
</style>

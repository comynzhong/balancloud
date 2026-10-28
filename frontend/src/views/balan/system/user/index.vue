<template>
  <div class="app-container user-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">SYSTEM · USER</p>
        <h2>用户管理</h2>
        <p class="page-desc">维护后台账号、固定角色与授权场站；停用或重置密码会立即让对方下线。</p>
      </div>
      <div class="page-actions">
        <el-button icon="Refresh" @click="getList">刷新</el-button>
        <el-button v-hasPermi="['system:user:add']" type="primary" icon="Plus" @click="handleAdd">新增账号</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card">
      <el-form v-show="showSearch" ref="queryRef" :model="queryParams" :inline="true" label-width="72px">
        <el-form-item label="账号" prop="keyword">
          <el-input
            v-model="queryParams.keyword"
            placeholder="登录名 / 姓名 / 邮箱 / 手机号"
            clearable
            style="width: 240px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="item in userStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select v-model="queryParams.roleId" placeholder="全部角色" clearable style="width: 160px">
            <el-option v-for="role in roleOptions" :key="role.roleId" :label="role.roleName" :value="role.roleId" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="toolbar">
        <el-col :span="1.5">
          <el-tag type="info" effect="plain">共 {{ total }} 个账号</el-tag>
        </el-col>
        <el-col :span="1.5">
          <el-tag type="warning" effect="plain">内置账号 admin 不可停用或删除</el-tag>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="userList" row-key="userId">
        <el-table-column label="账号" min-width="200">
          <template #default="{ row }">
            <div class="strong">{{ row.nickName }}</div>
            <div class="muted">
              {{ row.userName }}
              <el-tag v-if="row.pwdResetRequired === '1'" size="small" type="warning" effect="plain" class="pwd-tag">待改密</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="name in row.roleNames" :key="name" size="small" effect="plain" class="role-tag">
              {{ name }}
            </el-tag>
            <span v-if="!row.roleNames || !row.roleNames.length" class="muted">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="授权场站" min-width="200">
          <template #default="{ row }">
            <span v-if="isAdminRow(row)" class="muted">全部场站（超管）</span>
            <span v-else-if="!row.stationIds || !row.stationIds.length" class="muted">未授权</span>
            <span v-else>{{ stationNames(row.stationIds) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="联系方式" min-width="180">
          <template #default="{ row }">
            <div>{{ row.email || '-' }}</div>
            <div class="muted">{{ row.phoneNumber || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              active-value="0"
              inactive-value="1"
              :disabled="!canChangeStatus(row)"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="180">
          <template #default="{ row }">
            <div>{{ formatTime(row.loginDate) }}</div>
            <div class="muted">{{ row.loginIp || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-hasPermi="['system:user:edit']" link type="primary" icon="Edit" @click="handleUpdate(row)">编辑</el-button>
            <el-button v-hasPermi="['system:user:resetPwd']" link type="primary" icon="Key" @click="handleResetPwd(row)">重置密码</el-button>
            <el-button
              v-hasPermi="['system:user:remove']"
              link
              type="danger"
              icon="Delete"
              :disabled="!canRemove(row)"
              @click="handleDelete(row)"
            >删除</el-button>
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
      :title="form.userId ? '编辑账号' : '新增账号'"
      width="640px"
      append-to-body
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="登录名" prop="userName">
              <el-input v-model="form.userName" :disabled="!!form.userId" placeholder="字母、数字、下划线" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="姓名" prop="nickName">
              <el-input v-model="form.nickName" placeholder="请输入姓名" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col v-if="!form.userId" :span="24">
            <el-form-item label="初始密码" prop="password">
              <el-input v-model="form.password" type="password" show-password placeholder="6-30 位，不含空格" maxlength="30" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" placeholder="可选" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phoneNumber">
              <el-input v-model="form.phoneNumber" placeholder="可选" maxlength="20" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="角色" prop="roleIds">
              <el-select v-model="form.roleIds" multiple :disabled="grantLocked" placeholder="至少选择一个角色" style="width: 100%">
                <el-option v-for="role in roleOptions" :key="role.roleId" :label="role.roleName" :value="role.roleId" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="授权场站" prop="stationIds">
              <el-select
                v-model="form.stationIds"
                multiple
                :disabled="grantLocked"
                placeholder="不选则该账号看不到任何场站"
                style="width: 100%"
              >
                <el-option v-for="item in stationOptions" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status" :disabled="grantLocked">
                <el-radio label="0">正常</el-radio>
                <el-radio label="1">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit placeholder="补充说明" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div v-if="grantLocked && form.userId" class="lock-tip">
        当前账号是登录账号或内置超管，角色、场站授权与状态不可在此调整。
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addUser,
  changeUserStatus,
  delUser,
  getUser,
  listRole,
  listUser,
  resetUserPassword,
  updateUser
} from '@/api/balan/system'
import { getStationOptions } from '@/api/balan/station'
import { formatTime, userStatusOptions } from '@/utils/balan'
import useUserStore from '@/store/modules/user'

defineOptions({ name: 'BalanSystemUser' })

const userStore = useUserStore()

const SUPER_ADMIN_ID = 1

const loading = ref(false)
const submitLoading = ref(false)
const showSearch = ref(true)
const dialogVisible = ref(false)
const userList = ref([])
const total = ref(0)
const queryRef = ref()
const formRef = ref()
const dateRange = ref([])
const roleOptions = ref([])
const stationOptions = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  status: undefined,
  roleId: undefined,
  beginTime: undefined,
  endTime: undefined
})

const form = reactive({
  userId: undefined,
  userName: '',
  nickName: '',
  password: '',
  email: '',
  phoneNumber: '',
  status: '0',
  remark: '',
  roleIds: [],
  stationIds: []
})

const rules = computed(() => ({
  userName: [
    { required: true, message: '登录名不能为空', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{2,30}$/, message: '登录名只允许字母、数字、下划线，长度 2-30', trigger: 'blur' }
  ],
  nickName: [{ required: true, message: '姓名不能为空', trigger: 'blur' }],
  password: !form.userId
    ? [
        { required: true, message: '初始密码不能为空', trigger: 'blur' },
        { min: 6, max: 30, message: '密码长度需为 6-30 位', trigger: 'blur' }
      ]
    : [],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phoneNumber: [{ pattern: /^$|^\d{6,20}$/, message: '手机号只允许 6-20 位数字', trigger: 'blur' }],
  roleIds: [{ required: true, type: 'array', min: 1, message: '至少选择一个角色', trigger: 'change' }]
}))

const grantLocked = computed(() => {
  if (!form.userId) return false
  return form.userId === SUPER_ADMIN_ID || userStore.id === form.userId
})

function isAdminRow(row) {
  return row.userId === SUPER_ADMIN_ID
}

function isSelf(row) {
  return userStore.id === row.userId
}

function canChangeStatus(row) {
  return !isAdminRow(row) && !isSelf(row)
}

function canRemove(row) {
  return !isAdminRow(row) && !isSelf(row)
}

function stationNames(ids) {
  return ids
    .map(id => stationOptions.value.find(item => item.id === id)?.name || `#${id}`)
    .join('、')
}

async function getList() {
  loading.value = true
  try {
    const params = { ...queryParams, beginTime: dateRange.value?.[0], endTime: dateRange.value?.[1] }
    const res = await listUser(params)
    userList.value = res.rows || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryRef.value?.resetFields()
  dateRange.value = []
  handleQuery()
}

async function loadOptions() {
  const [roleRes, stationRes] = await Promise.all([listRole(), getStationOptions()])
  roleOptions.value = roleRes.data || []
  stationOptions.value = stationRes.data || []
}

function handleAdd() {
  resetFormData()
  dialogVisible.value = true
}

async function handleUpdate(row) {
  resetFormData()
  const res = await getUser(row.userId)
  const detail = res.data || row
  Object.assign(form, {
    userId: detail.userId,
    userName: detail.userName,
    nickName: detail.nickName,
    email: detail.email || '',
    phoneNumber: detail.phoneNumber || '',
    status: detail.status,
    remark: detail.remark || '',
    roleIds: detail.roleIds || [],
    stationIds: detail.stationIds || []
  })
  dialogVisible.value = true
}

function resetFormData() {
  Object.assign(form, {
    userId: undefined,
    userName: '',
    nickName: '',
    password: '',
    email: '',
    phoneNumber: '',
    status: '0',
    remark: '',
    roleIds: [],
    stationIds: []
  })
  formRef.value?.clearValidate()
}

function resetForm() {
  resetFormData()
}

function submitForm() {
  formRef.value?.validate(async valid => {
    if (!valid) return
    submitLoading.value = true
    try {
      const payload = {
        userName: form.userName,
        nickName: form.nickName,
        password: form.password || undefined,
        email: form.email,
        phoneNumber: form.phoneNumber,
        status: form.status,
        remark: form.remark,
        roleIds: form.roleIds,
        stationIds: form.stationIds
      }
      if (form.userId) {
        await updateUser(form.userId, payload)
        ElMessage.success('账号已更新')
      } else {
        await addUser(payload)
        ElMessage.success('账号已创建')
      }
      dialogVisible.value = false
      getList()
    } finally {
      submitLoading.value = false
    }
  })
}

function handleStatusChange(row) {
  const next = row.status
  const action = next === '0' ? '启用' : '停用'
  ElMessageBox.confirm(`确认${action}账号「${row.nickName}」？停用后该账号的在线会话会立即失效。`, `${action}账号`, {
    type: 'warning',
    confirmButtonText: action,
    cancelButtonText: '取消'
  }).then(() => changeUserStatus(row.userId, next)).then(() => {
    ElMessage.success(`已${action}`)
  }).catch(() => {
    row.status = next === '0' ? '1' : '0'
  })
}

function handleResetPwd(row) {
  ElMessageBox.prompt(`为「${row.nickName}」设置新密码（6-30 位，不含空格）`, '重置密码', {
    inputType: 'password',
    inputPattern: /^[^\s]{6,30}$/,
    inputErrorMessage: '密码长度需为 6-30 位且不含空格',
    confirmButtonText: '重置',
    cancelButtonText: '取消'
  }).then(({ value }) => resetUserPassword(row.userId, value)).then(() => {
    ElMessage.success('密码已重置，该账号需重新登录')
  }).catch(() => {})
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除账号「${row.nickName}」？删除后该账号立即下线。`, '删除账号', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(() => delUser(row.userId)).then(() => {
    ElMessage.success('账号已删除')
    getList()
  }).catch(() => {})
}

onMounted(async () => {
  await loadOptions()
  getList()
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

.summary-row {
  margin-bottom: 14px;
}

.table-card {
  border-radius: 10px;
}

.toolbar {
  margin-bottom: 10px;
}

.strong {
  font-weight: 600;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.role-tag {
  margin-right: 6px;
}

.pwd-tag {
  margin-left: 6px;
  transform: scale(0.9);
}

.lock-tip {
  margin-top: 6px;
  padding: 8px 10px;
  font-size: 12px;
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
  border-radius: 6px;
}
</style>

<template>
  <div class="app-container role-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">SYSTEM · ROLE</p>
        <h2>角色管理</h2>
        <p class="page-desc">角色可自建，权限树按菜单与按钮勾选；授权、启停、删除会立即让该角色下的账号下线。</p>
      </div>
      <div class="page-actions">
        <el-button v-hasPermi="['system:role:add']" type="primary" icon="Plus" @click="openCreate">新增角色</el-button>
        <el-button icon="Refresh" @click="getList">刷新</el-button>
      </div>
    </div>

    <el-alert
      class="fixed-tip"
      type="info"
      show-icon
      :closable="false"
      title="内置角色（超管 / 场站运营 / 运维 / 访客）不允许删除，角色标识也不可变；超管权限由 *:*:* 兜底，不在此页维护。"
    />

    <el-card shadow="never" class="table-card">
      <el-table v-loading="loading" :data="roleList" row-key="roleId">
        <el-table-column label="角色" min-width="220">
          <template #default="{ row }">
            <div class="strong">
              {{ row.roleName }}
              <el-tag v-if="row.builtin === '1'" size="small" type="info" effect="plain">内置</el-tag>
            </div>
            <div class="muted">{{ row.roleKey }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="roleSort" label="显示顺序" width="100" align="center" />
        <el-table-column label="账号数" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.userCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="权限项" width="100" align="center">
          <template #default="{ row }">{{ row.menuCount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small" effect="plain">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="220">
          <template #default="{ row }">
            <div>{{ row.remark || '-' }}</div>
            <div class="muted">创建于 {{ formatTime(row.createTime) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button v-hasPermi="['system:role:query']" link type="primary" icon="View" @click="openView(row)">
              查看权限
            </el-button>
            <el-button v-hasPermi="['system:role:edit']" link type="primary" icon="Edit" @click="openEdit(row)">
              编辑
            </el-button>
            <el-button
              v-if="row.roleId !== SUPER_ADMIN_ID"
              v-hasPermi="['system:role:edit']"
              link
              :type="row.status === '0' ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === '0' ? '停用' : '启用' }}
            </el-button>
            <el-button
              v-if="row.builtin !== '1'"
              v-hasPermi="['system:role:remove']"
              link
              type="danger"
              icon="Delete"
              @click="removeRole(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="viewVisible" :title="`${currentRole?.roleName || ''} 权限`" size="42%">
      <div v-loading="viewLoading" class="tree-body">
        <div class="tree-summary">
          <el-tag type="info" effect="plain">{{ viewCheckedCount }} 个权限项</el-tag>
          <span class="muted">此处只读；要调整权限请用列表里的「编辑」。</span>
        </div>
        <el-tree
          ref="viewTreeRef"
          :data="viewMenuTree"
          :props="viewTreeProps"
          node-key="id"
          show-checkbox
          check-strictly
          default-expand-all
          class="perm-tree"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span>{{ data.label }}</span>
              <el-tag v-if="data.menuType === 'F'" size="small" type="warning" effect="plain">按钮</el-tag>
              <el-tag v-else-if="data.menuType === 'C'" size="small" type="success" effect="plain">菜单</el-tag>
              <el-tag v-else size="small" effect="plain">目录</el-tag>
              <code v-if="data.perms" class="perm-code">{{ data.perms }}</code>
            </div>
          </template>
        </el-tree>
      </div>
    </el-drawer>

    <el-drawer v-model="formVisible" :title="form.roleId ? '编辑角色' : '新增角色'" size="46%">
      <div v-loading="formLoading" class="form-body">
        <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
          <el-form-item label="角色名称" prop="roleName">
            <el-input v-model="form.roleName" maxlength="30" placeholder="如：财务" />
          </el-form-item>
          <el-form-item label="角色标识" prop="roleKey">
            <el-input
              v-model="form.roleKey"
              maxlength="50"
              :disabled="isBuiltin"
              placeholder="小写字母开头，如：finance"
            />
            <div v-if="isBuiltin" class="muted">内置角色的标识不可修改，改名请用角色名称。</div>
          </el-form-item>
          <el-form-item label="显示顺序" prop="roleSort">
            <el-input-number v-model="form.roleSort" :min="0" :max="999" controls-position="right" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status" :disabled="isSuperAdmin">
              <el-radio label="0">正常</el-radio>
              <el-radio label="1">停用</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="说明">
            <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
          </el-form-item>
        </el-form>

        <el-divider content-position="left">菜单权限</el-divider>
        <el-alert
          v-if="isSuperAdmin"
          class="tree-tip"
          type="warning"
          show-icon
          :closable="false"
          title="超级管理员由 *:*:* 全量放行，权限树不在此页维护。"
        />
        <el-alert
          v-else-if="!canGrant"
          class="tree-tip"
          type="info"
          show-icon
          :closable="false"
          title="当前账号没有 system:role:grant 权限，保存只更新基本信息，权限树不提交。"
        />
        <template v-else>
          <div class="tree-summary">
            <el-tag type="info" effect="plain">{{ checkedCount }} 项</el-tag>
            <span class="muted">勾到哪个层级就授权到哪个层级；保存时后端自动补齐上级目录，否则侧边栏显示不出来。</span>
          </div>
          <el-tree
            ref="treeRef"
            :data="menuTree"
            :props="treeProps"
            node-key="id"
            show-checkbox
            check-strictly
            default-expand-all
            class="perm-tree"
            @check="onCheck"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <span>{{ data.label }}</span>
                <el-tag v-if="data.menuType === 'F'" size="small" type="warning" effect="plain">按钮</el-tag>
                <el-tag v-else-if="data.menuType === 'C'" size="small" type="success" effect="plain">菜单</el-tag>
                <el-tag v-else size="small" effect="plain">目录</el-tag>
                <code v-if="data.perms" class="perm-code">{{ data.perms }}</code>
              </div>
            </template>
          </el-tree>
        </template>

        <div class="drawer-footer">
          <el-button @click="formVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitForm">
            {{ form.roleId ? '保存' : '创建角色' }}
          </el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addRole,
  changeRoleStatus,
  delRole,
  grantableMenuTree,
  getRoleMenus,
  grantRoleMenus,
  listRole,
  updateRole
} from '@/api/balan/system'
import { checkPermi } from '@/utils/permission'
import { formatTime } from '@/utils/balan'

defineOptions({ name: 'BalanSystemRole' })

const SUPER_ADMIN_ID = 1

const loading = ref(false)
const viewLoading = ref(false)
const formLoading = ref(false)
const submitting = ref(false)
const viewVisible = ref(false)
const formVisible = ref(false)
const roleList = ref([])
const menuTree = ref([])
const viewMenuTree = ref([])
const checkedCount = ref(0)
const viewCheckedCount = ref(0)
const currentRole = ref(null)
const formRef = ref()
const treeRef = ref()
const viewTreeRef = ref()

const form = ref(emptyForm())

const treeProps = { label: 'label', children: 'children' }
const viewTreeProps = { label: 'label', children: 'children', disabled: 'locked' }

/** el-tree 没有整体只读开关，只读视图靠逐节点 disabled 挡住勾选。 */
function lockNodes(nodes) {
  nodes.forEach(node => {
    node.locked = true
    if (node.children?.length) {
      lockNodes(node.children)
    }
  })
  return nodes
}

const rules = {
  roleName: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }],
  roleKey: [
    { required: true, message: '角色标识不能为空', trigger: 'blur' },
    { pattern: /^[a-z][a-z0-9_]{1,49}$/, message: '小写字母开头，允许小写字母、数字、下划线，长度 2-50', trigger: 'blur' }
  ],
  roleSort: [{ required: true, message: '显示顺序不能为空', trigger: 'blur' }]
}

const isSuperAdmin = computed(() => form.value.roleId === SUPER_ADMIN_ID)
const isBuiltin = computed(() => form.value.builtin === '1')
const canGrant = computed(() => checkPermi(['system:role:grant']))

function emptyForm() {
  return { roleId: null, roleName: '', roleKey: '', roleSort: 10, status: '0', remark: '', builtin: '0' }
}

async function getList() {
  loading.value = true
  try {
    const res = await listRole()
    roleList.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function openView(row) {
  currentRole.value = row
  viewVisible.value = true
  viewLoading.value = true
  try {
    const [treeRes, menuRes] = await Promise.all([grantableMenuTree(), getRoleMenus(row.roleId)])
    viewMenuTree.value = lockNodes(treeRes.data || [])
    await nextTick()
    // 只读视图用 check-strictly 精确回填库里的授权，避免联动模式把整棵子树点亮。
    viewCheckedCount.value = (menuRes.data.checkedMenuIds || []).length
    viewTreeRef.value?.setCheckedKeys(menuRes.data.checkedMenuIds || [], false)
  } finally {
    viewLoading.value = false
  }
}

async function openCreate() {
  form.value = emptyForm()
  formVisible.value = true
  formLoading.value = true
  try {
    const res = await grantableMenuTree()
    menuTree.value = res.data || []
    await nextTick()
    treeRef.value?.setCheckedKeys([], false)
    checkedCount.value = 0
  } finally {
    formLoading.value = false
  }
}

async function openEdit(row) {
  form.value = {
    roleId: row.roleId,
    roleName: row.roleName,
    roleKey: row.roleKey,
    roleSort: row.roleSort,
    status: row.status,
    remark: row.remark || '',
    builtin: row.builtin
  }
  formVisible.value = true
  formLoading.value = true
  try {
    const [treeRes, menuRes] = await Promise.all([grantableMenuTree(), getRoleMenus(row.roleId)])
    menuTree.value = treeRes.data || []
    await nextTick()
    if (canGrant.value && !isSuperAdmin.value) {
      // 必须用 check-strictly 精确回填：联动模式下勾一个已授权的菜单会把它下面未授权的按钮一起勾上，
      // 保存就等于悄悄加权限（实测 viewer 库里 18 项，联动回填会变成 20 项）。
      treeRef.value?.setCheckedKeys(menuRes.data.checkedMenuIds || [], false)
    }
    syncCheckedCount()
  } finally {
    formLoading.value = false
  }
}

function collectChecked() {
  return treeRef.value?.getCheckedKeys() || []
}

function syncCheckedCount() {
  checkedCount.value = isSuperAdmin.value || !canGrant.value ? 0 : collectChecked().length
}

function onCheck() {
  syncCheckedCount()
}

async function submitForm() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  const payload = {
    roleName: form.value.roleName,
    roleKey: form.value.roleKey,
    roleSort: form.value.roleSort,
    status: form.value.status,
    remark: form.value.remark
  }
  const editGrantable = form.value.roleId && !isSuperAdmin.value && canGrant.value
  submitting.value = true
  try {
    if (form.value.roleId) {
      await updateRole(form.value.roleId, payload)
      if (editGrantable) {
        await grantRoleMenus(form.value.roleId, collectChecked())
      }
      ElMessage.success('角色已保存')
    } else {
      payload.menuIds = canGrant.value ? collectChecked() : []
      await addRole(payload)
      ElMessage.success('角色已创建')
    }
    formVisible.value = false
    await getList()
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  const next = row.status === '0' ? '1' : '0'
  const action = next === '1' ? '停用' : '启用'
  const confirmed = await ElMessageBox.confirm(
    next === '1'
      ? `确定停用角色「${row.roleName}」？该角色下 ${row.userCount} 个账号会立即下线，菜单与按钮权限同时收回。`
      : `确定启用角色「${row.roleName}」？该角色下账号重新登录后权限恢复。`,
    `${action}角色`,
    { type: 'warning' }
  ).catch(() => false)
  if (!confirmed) {
    return
  }
  await changeRoleStatus(row.roleId, next)
  ElMessage.success(`角色已${action}`)
  await getList()
}

async function removeRole(row) {
  const confirmed = await ElMessageBox.confirm(
    `确定删除角色「${row.roleName}」？该角色的菜单授权会一并清除。`,
    '删除角色',
    { type: 'warning' }
  ).catch(() => false)
  if (!confirmed) {
    return
  }
  await delRole(row.roleId)
  ElMessage.success('角色已删除')
  await getList()
}

onMounted(getList)
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

.fixed-tip {
  margin-bottom: 14px;
}

.table-card {
  border-radius: 10px;
}

.strong {
  font-weight: 600;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.tree-summary {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.tree-tip {
  margin-bottom: 12px;
}

.perm-tree {
  max-height: 46vh;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px;
}

.tree-node {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.perm-code {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 16px;
}
</style>

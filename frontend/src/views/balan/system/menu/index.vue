<template>
  <div class="app-container menu-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">SYSTEM · MENU</p>
        <h2>菜单管理</h2>
        <p class="page-desc">
          维护左侧导航与按钮权限。菜单树由 <code>/getRouters</code> 实时读取，改完刷新页面即生效；
          非管理员账号还需在「角色管理」里勾选，并重新登录取新权限。
        </p>
      </div>
      <div class="page-actions">
        <el-button icon="Sort" @click="toggleExpandAll">{{ expandAll ? '全部折叠' : '全部展开' }}</el-button>
        <el-button icon="Refresh" @click="getList">刷新</el-button>
        <el-button v-hasPermi="['system:menu:add']" type="primary" icon="Plus" @click="openDialog()">新增菜单</el-button>
      </div>
    </div>

    <el-card shadow="never" class="panel-card">
      <el-form :model="queryParams" inline class="query-form" @submit.prevent>
        <el-form-item label="菜单名称">
          <el-input v-model="queryParams.menuName" placeholder="请输入菜单名称" clearable style="width: 180px" @keyup.enter="getList" />
        </el-form-item>
        <el-form-item label="菜单状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="显示状态">
          <el-select v-model="queryParams.visible" placeholder="全部" clearable style="width: 130px">
            <el-option label="显示" value="0" />
            <el-option label="隐藏" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getList">查询</el-button>
          <el-button icon="RefreshLeft" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        v-if="refreshTable"
        v-loading="loading"
        :data="menuTree"
        row-key="menuId"
        :default-expand-all="expandAll"
        :tree-props="{ children: 'children' }"
        empty-text="没有匹配的菜单"
      >
        <el-table-column label="菜单名称" prop="menuName" min-width="190">
          <template #default="{ row }">
            <span class="menu-name">
              <svg-icon v-if="row.icon && row.icon !== '#'" :icon-class="row.icon" class-name="menu-icon" />
              {{ row.menuName }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="86" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.menuType)" effect="plain">{{ typeLabel(row.menuType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="路由地址" prop="path" min-width="130" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.path || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="组件路径" prop="component" min-width="170" show-overflow-tooltip>
          <template #default="{ row }"><span class="mono">{{ row.component || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="权限标识" prop="perms" min-width="150" show-overflow-tooltip>
          <template #default="{ row }"><code v-if="row.perms" class="mono">{{ row.perms }}</code><span v-else>—</span></template>
        </el-table-column>
        <el-table-column label="排序" prop="orderNum" width="70" align="center" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '0' ? 'success' : 'danger'" effect="plain">
              {{ row.status === '0' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="212" align="right" fixed="right" class-name="op-cell">
          <template #default="{ row }">
            <el-button v-hasPermi="['system:menu:edit']" link type="primary" icon="Edit" @click="openDialog(row)">修改</el-button>
            <el-button v-if="row.menuType !== 'F'" v-hasPermi="['system:menu:add']" link type="primary" icon="Plus" @click="openDialog(null, row)">新增</el-button>
            <el-button v-hasPermi="['system:menu:remove']" link type="danger" icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="98px">
        <el-form-item label="上级菜单" prop="parentId">
          <el-tree-select
            v-model="form.parentId"
            :data="parentOptions"
            :props="{ children: 'children', label: 'label' }"
            node-key="id"
            check-strictly
            default-expand-all
            placeholder="选择上级菜单"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="菜单类型" prop="menuType">
          <el-radio-group v-model="form.menuType">
            <el-radio-button label="M" :disabled="!allowBranch">目录</el-radio-button>
            <el-radio-button label="C" :disabled="!allowBranch">菜单</el-radio-button>
            <el-radio-button label="F" :disabled="!allowButton">按钮</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="菜单名称" prop="menuName">
          <el-input v-model="form.menuName" placeholder="请输入菜单名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="显示排序" prop="orderNum">
          <el-input-number v-model="form.orderNum" :min="0" :max="9999" controls-position="right" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 'F'" label="菜单图标">
          <el-popover placement="bottom-start" :width="440" trigger="click" popper-class="icon-popover">
            <template #reference>
              <el-input v-model="form.icon" placeholder="点击选择图标" readonly>
                <template #prefix>
                  <svg-icon v-if="form.icon && form.icon !== '#'" :icon-class="form.icon" class="icon-prefix" />
                </template>
              </el-input>
            </template>
            <icon-select :active-icon="form.icon" @selected="onIconSelected" />
          </el-popover>
        </el-form-item>
        <el-form-item v-if="form.menuType !== 'F'" label="路由地址" prop="path">
          <el-input v-model="form.path" placeholder="如 station；外链以 http 开头" />
        </el-form-item>
        <el-form-item v-if="form.menuType === 'C' && !isExternal" label="组件路径" prop="component">
          <el-input v-model="form.component" placeholder="views 下的相对路径，如 balan/station/index" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 'M'" label="权限标识" prop="perms">
          <el-input v-model="form.perms" placeholder="如 station:list，后端 @ss.hasPermi 使用" />
        </el-form-item>
        <el-form-item v-if="form.menuType !== 'F'" label="显示状态">
          <el-radio-group v-model="form.visible">
            <el-radio label="0">显示</el-radio>
            <el-radio label="1">隐藏</el-radio>
          </el-radio-group>
          <span class="form-tip">隐藏仍可通过路由访问，只是不出现在侧边栏</span>
        </el-form-item>
        <el-form-item label="菜单状态">
          <el-radio-group v-model="form.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
          <span class="form-tip">停用后不下发路由，按钮权限同时失效</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { addMenu, deleteMenu, listMenu, menuOptions, updateMenu } from '@/api/balan/menu'
import IconSelect from '@/components/IconSelect'

defineOptions({ name: 'BalanMenu' })

const { proxy } = getCurrentInstance()

const loading = ref(false)
const saving = ref(false)
const expandAll = ref(true)
const refreshTable = ref(true)
const dialogVisible = ref(false)
const flatRows = ref([])
const parentOptions = ref([])
const editingId = ref(null)
const formRef = ref()

const queryParams = reactive({ menuName: '', status: '', visible: '' })

const form = reactive({
  parentId: 0,
  menuType: 'C',
  menuName: '',
  orderNum: 1,
  path: '',
  component: '',
  perms: '',
  icon: '#',
  visible: '0',
  status: '0'
})

const menuTree = computed(() => buildTree(flatRows.value))
const dialogTitle = computed(() => (editingId.value ? '修改菜单' : '新增菜单'))
const isExternal = computed(() => /^https?:\/\//.test(form.path || ''))

// 与后端 SysMenuAdminService 的结构约束保持一致：目录/菜单只能挂在目录或顶级下，菜单下只放按钮
const parentType = computed(() => {
  if (!form.parentId) {
    return 'ROOT'
  }
  const found = flatRows.value.find(row => row.menuId === form.parentId)
  return found ? found.menuType : 'ROOT'
})
const allowBranch = computed(() => ['ROOT', 'M'].includes(parentType.value))
const allowButton = computed(() => ['M', 'C'].includes(parentType.value))

watch(parentType, type => {
  if (type === 'C' && form.menuType !== 'F') {
    form.menuType = 'F'
  } else if (type !== 'C' && form.menuType === 'F' && !allowButton.value) {
    form.menuType = 'C'
  }
})

const rules = {
  menuName: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  orderNum: [{ required: true, message: '请输入显示排序', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'change' }],
  path: [{
    validator: (rule, value, callback) => {
      if (form.menuType !== 'F' && !isExternal.value && !(value || '').trim()) {
        callback(new Error('请输入路由地址'))
        return
      }
      callback()
    },
    trigger: 'blur'
  }],
  component: [{
    validator: (rule, value, callback) => {
      if (form.menuType === 'C' && !isExternal.value && !(value || '').trim()) {
        callback(new Error('请输入组件路径'))
        return
      }
      callback()
    },
    trigger: 'blur'
  }],
  perms: [{
    validator: (rule, value, callback) => {
      if (form.menuType === 'F' && !(value || '').trim()) {
        callback(new Error('按钮必须填写权限标识'))
        return
      }
      callback()
    },
    trigger: 'blur'
  }]
}

function typeLabel(type) {
  return { M: '目录', C: '菜单', F: '按钮' }[type] || type
}

function typeTag(type) {
  return { M: 'warning', C: 'success', F: 'info' }[type] || 'info'
}

function sortNodes(nodes) {
  nodes.sort((a, b) => (a.orderNum - b.orderNum) || (a.menuId - b.menuId))
  nodes.forEach(node => node.children && sortNodes(node.children))
  return nodes
}

function buildTree(rows) {
  const nodes = new Map(rows.map(row => [row.menuId, { ...row, children: [] }]))
  const roots = []
  nodes.forEach(node => {
    const parent = nodes.get(node.parentId)
    if (parent && parent.menuId !== node.menuId) {
      parent.children.push(node)
    } else {
      roots.push(node)
    }
  })
  // 空 children 会让表格画出多余的展开箭头
  const trim = list => list.forEach(node => {
    if (node.children && node.children.length) {
      trim(node.children)
    } else {
      delete node.children
    }
  })
  trim(roots)
  return sortNodes(roots)
}

function getList() {
  loading.value = true
  return listMenu(queryParams).then(res => {
    flatRows.value = res.data || []
  }).finally(() => {
    loading.value = false
  })
}

function getOptions(excludeId) {
  return menuOptions(excludeId).then(res => {
    parentOptions.value = [{ id: 0, label: '主类目', children: res.data || [] }]
  })
}

function resetQuery() {
  queryParams.menuName = ''
  queryParams.status = ''
  queryParams.visible = ''
  getList()
}

function toggleExpandAll() {
  refreshTable.value = false
  expandAll.value = !expandAll.value
  nextTick(() => {
    refreshTable.value = true
  })
}

function openDialog(row, parent) {
  editingId.value = row ? row.menuId : null
  getOptions(editingId.value)
  Object.assign(form, {
    parentId: row ? row.parentId : (parent ? parent.menuId : 0),
    menuType: row ? row.menuType : (parent ? (parent.menuType === 'M' ? 'C' : 'F') : 'C'),
    menuName: row ? row.menuName : '',
    orderNum: row ? row.orderNum : nextOrder(parent),
    path: row ? (row.path || '') : '',
    component: row ? (row.component || '') : '',
    perms: row ? (row.perms || '') : '',
    icon: row ? (row.icon || '#') : '#',
    visible: row ? row.visible : '0',
    status: row ? row.status : '0'
  })
  dialogVisible.value = true
}

function nextOrder(parent) {
  if (!parent) {
    return flatRows.value.filter(row => row.parentId === 0).length + 1
  }
  return flatRows.value.filter(row => row.parentId === parent.menuId).length + 1
}

function onIconSelected(name) {
  form.icon = name
}

function resetForm() {
  formRef.value?.clearValidate()
  editingId.value = null
}

function payload() {
  return {
    menuName: form.menuName,
    parentId: form.parentId,
    orderNum: form.orderNum,
    path: form.path,
    component: form.component,
    query: '',
    isFrame: '1',
    isCache: '0',
    menuType: form.menuType,
    visible: form.visible,
    status: form.status,
    perms: form.perms,
    icon: form.icon
  }
}

function submitForm() {
  formRef.value?.validate(valid => {
    if (!valid) {
      return
    }
    saving.value = true
    const body = payload()
    const request = editingId.value ? updateMenu(editingId.value, body) : addMenu(body)
    request.then(() => {
      proxy.$modal.msgSuccess(editingId.value ? '修改成功' : '新增成功')
      dialogVisible.value = false
      return getList()
    }).finally(() => {
      saving.value = false
    })
  })
}

function handleDelete(row) {
  proxy.$modal.confirm(`确认删除菜单「${row.menuName}」吗？`).then(() => deleteMenu(row.menuId)).then(() => {
    proxy.$modal.msgSuccess('删除成功')
    return getList()
  }).catch(() => {})
}

onMounted(getList)
</script>

<style lang="scss" scoped>
.menu-page {
  .menu-name {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-weight: 600;
    color: var(--bc-text);
  }

  .menu-icon {
    width: 15px;
    height: 15px;
    color: var(--bc-brand-deep);
  }

  .mono {
    font-family: Consolas, Monaco, 'Courier New', monospace;
    font-size: 12px;
    color: var(--bc-text-muted);
  }

  .query-form {
    margin-bottom: 6px;
  }

  .form-tip {
    margin-left: 10px;
    font-size: 12px;
    color: #a2b1a9;
  }

  .icon-prefix {
    width: 16px;
    height: 16px;
  }

  :deep(.op-cell .cell) {
    white-space: nowrap;
  }
}

:deep(.icon-popover) {
  padding: 8px;
}
</style>

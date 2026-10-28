<template>
  <div class="app-container config-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">SYSTEM · CONFIG</p>
        <h2>系统配置</h2>
        <p class="page-desc">
          平台级运行参数，保存后立即刷新配置缓存，业务接口无需重启。配置不按租户拆分，
          多租户隔离始终生效；此处的「多租户」开关只影响产品表面。
        </p>
      </div>
      <div class="page-actions">
        <el-button
          v-hasPermi="['system:config:manage']"
          icon="Plus"
          :disabled="loading"
          @click="openCreate()"
        >
          新增配置项
        </el-button>
        <el-button icon="Refresh" :disabled="saving" @click="load">重新加载</el-button>
        <el-button
          v-hasPermi="['system:config:edit']"
          type="primary"
          icon="Check"
          :loading="saving"
          :disabled="!hasChanges"
          @click="save"
        >
          保存修改{{ hasChanges ? `（${dirtyKeys.length}）` : '' }}
        </el-button>
      </div>
    </div>

    <div class="scope-note">
      <el-icon class="note-icon"><InfoFilled /></el-icon>
      <div class="note-body">
        <p class="note-title">全局平台级</p>
        <p class="note-desc">
          所有配置项全平台共用，不随租户区分。版本内置项只能改值，以 ext. 开头的扩展项可在界面增删改；
          保存后验证码、小程序限额、支付、退款、仿真器与维护开关即时生效，维护模式只暂停小程序发起的新充电。
        </p>
      </div>
    </div>

    <div ref="gridRef" v-loading="loading" class="group-grid">
      <div v-for="(column, index) in columns" :key="index" class="group-col">
        <el-card v-for="group in column" :key="group.group" shadow="never" class="group-card">
        <template #header>
          <div class="group-head">
            <div class="group-title">
              <span class="group-name">{{ group.groupName }}</span>
              <code class="group-key">{{ group.group }}</code>
            </div>
            <span v-if="changedIn(group)" class="dirty-tag">{{ changedIn(group) }} 项待保存</span>
          </div>
        </template>

        <div class="item-list">
          <div v-for="item in group.items" :key="item.configKey" class="config-item">
            <div class="item-meta">
              <div class="item-name">
                {{ item.configName }}
                <el-tag v-if="!item.builtin" size="small" type="success" effect="plain">扩展</el-tag>
                <el-tag v-if="!item.editable" size="small" type="info" effect="plain">只读</el-tag>
              </div>
              <p v-if="item.remark" class="item-remark">{{ item.remark }}</p>
              <code class="item-key">{{ item.configKey }}</code>
            </div>

            <div class="item-control">
              <el-switch
                v-if="item.valueType === 'BOOLEAN'"
                v-model="values[item.configKey]"
                :disabled="!item.editable"
                class="bool-switch"
              />

              <el-select
                v-else-if="item.valueType === 'ENUM'"
                v-model="values[item.configKey]"
                :disabled="!item.editable"
                class="enum-select"
              >
                <el-option
                  v-for="option in item.options"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                  :disabled="option.disabled"
                />
              </el-select>

              <el-input-number
                v-else-if="item.valueType === 'INT' || item.valueType === 'DECIMAL'"
                v-model="values[item.configKey]"
                :min="item.minValue ?? undefined"
                :max="item.maxValue ?? undefined"
                :precision="item.valueType === 'DECIMAL' ? 2 : 0"
                :step="item.valueType === 'DECIMAL' ? 1 : 1"
                controls-position="right"
                :disabled="!item.editable"
                class="number-input"
              />

              <el-input
                v-else-if="item.maxLength > 80"
                v-model="values[item.configKey]"
                type="textarea"
                :rows="2"
                :maxlength="item.maxLength || undefined"
                show-word-limit
                :disabled="!item.editable"
                class="text-input is-area"
              />

              <el-input
                v-else
                v-model="values[item.configKey]"
                :maxlength="item.maxLength || undefined"
                show-word-limit
                :disabled="!item.editable"
                class="text-input"
              />

              <p v-if="item.configKey === 'tenant.multiTenant.enabled'" class="control-note">
                仅展示层标识；数据隔离始终开启，租户编码登录与入驻在 T1 落地。
              </p>
              <p v-else-if="item.configKey === 'payment.enabled'" class="control-note">
                关闭后小程序订单支付与钱包充值一并停用；退款仍可在后台正常发起。
              </p>
              <p v-else-if="item.configKey === 'payment.mode'" class="control-note">
                真实支付通道尚未接入，微信支付选项暂锁定；下方商户参数仅为接入预留，密钥与证书不入库、走环境变量。
              </p>
              <p v-else-if="item.configKey === 'security.captcha.enabled'" class="control-note">
                关闭后登录页隐藏滑块验证，并跳过登录时的令牌校验。
              </p>
              <p v-else-if="item.configKey === 'operations.simulator.enabled'" class="control-note">
                关闭后停用启动、批量建点与故障注入，停止入口保持可用。
              </p>

              <div
                v-if="!item.builtin"
                v-hasPermi="['system:config:manage']"
                class="item-tools"
              >
                <el-button link type="primary" size="small" icon="Edit" @click="openEdit(item, group.group)">
                  编辑
                </el-button>
                <el-button link type="danger" size="small" icon="Delete" @click="removeItem(item)">
                  删除
                </el-button>
              </div>
            </div>
          </div>
        </div>
        </el-card>
      </div>
    </div>

    <el-dialog
      v-model="itemDialog.visible"
      :title="itemDialog.editing ? '编辑扩展配置项' : '新增扩展配置项'"
      width="620px"
      append-to-body
      destroy-on-close
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemRules" label-width="96px">
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="itemForm.configKey" :disabled="itemDialog.editing" placeholder="ext.demoKey" />
          <p class="form-tip">统一使用 ext. 前缀，只允许字母、数字、下划线与点；创建后不可修改。</p>
        </el-form-item>
        <el-form-item label="名称" prop="configName">
          <el-input v-model="itemForm.configName" maxlength="100" placeholder="展示在配置页的名称" />
        </el-form-item>
        <el-form-item label="分组" prop="configGroup">
          <el-select v-model="itemForm.configGroup" class="form-select">
            <el-option
              v-for="group in groups"
              :key="group.group"
              :label="`${group.groupName}（${group.group}）`"
              :value="group.group"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="valueType">
          <el-select v-model="itemForm.valueType" class="form-select">
            <el-option v-for="type in typeOptions" :key="type.value" :label="type.label" :value="type.value" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="itemForm.valueType === 'ENUM'" label="枚举选项">
          <div class="option-editor">
            <div v-for="(option, index) in itemForm.options" :key="index" class="option-row">
              <el-input v-model="option.label" placeholder="显示名称" />
              <el-input v-model="option.value" placeholder="取值" />
              <el-button link type="danger" icon="Delete" @click="itemForm.options.splice(index, 1)" />
            </div>
            <el-button
              link
              type="primary"
              icon="Plus"
              @click="itemForm.options.push({ label: '', value: '', disabled: false })"
            >
              添加选项
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="当前值" prop="configValue">
          <el-switch v-if="itemForm.valueType === 'BOOLEAN'" v-model="itemForm.configValue" />
          <el-select
            v-else-if="itemForm.valueType === 'ENUM'"
            v-model="itemForm.configValue"
            class="form-select"
            placeholder="选择默认值"
          >
            <el-option
              v-for="option in itemForm.options"
              :key="option.value"
              :label="option.label || option.value"
              :value="option.value"
              :disabled="option.disabled"
            />
          </el-select>
          <el-input-number
            v-else-if="itemForm.valueType === 'INT' || itemForm.valueType === 'DECIMAL'"
            v-model="itemForm.configValue"
            :precision="itemForm.valueType === 'DECIMAL' ? 2 : 0"
            :min="itemForm.minValue ?? undefined"
            :max="itemForm.maxValue ?? undefined"
            controls-position="right"
            class="form-number"
          />
          <el-input v-else v-model="itemForm.configValue" maxlength="500" show-word-limit />
        </el-form-item>

        <template v-if="itemForm.valueType === 'INT' || itemForm.valueType === 'DECIMAL'">
          <el-form-item label="数值下限">
            <el-input-number
              v-model="itemForm.minValue"
              :precision="itemForm.valueType === 'DECIMAL' ? 2 : 0"
              controls-position="right"
              class="form-number"
            />
          </el-form-item>
          <el-form-item label="数值上限">
            <el-input-number
              v-model="itemForm.maxValue"
              :precision="itemForm.valueType === 'DECIMAL' ? 2 : 0"
              controls-position="right"
              class="form-number"
            />
          </el-form-item>
        </template>

        <el-form-item v-if="itemForm.valueType === 'STRING'" label="长度上限">
          <el-input-number v-model="itemForm.maxLength" :min="1" :max="500" controls-position="right" class="form-number" />
          <p class="form-tip">留空表示不额外限制，配置值最长 500 个字符。</p>
        </el-form-item>

        <el-form-item label="说明">
          <el-input v-model="itemForm.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="itemSaving" @click="submitItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createConfigItem,
  deleteConfigItem,
  getConfigGroups,
  updateConfig,
  updateConfigItem
} from '@/api/balan/runtime'
import useRuntimeStore from '@/store/modules/runtime'

const runtimeStore = useRuntimeStore()

const groups = ref([])
const values = ref({})
const initial = ref({})
const loading = ref(false)
const saving = ref(false)

const dirtyKeys = computed(() => Object.keys(values.value).filter(
  key => normalize(values.value[key]) !== initial.value[key]
))
const hasChanges = computed(() => dirtyKeys.value.length > 0)

function normalize(value) {
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  if (value === null || value === undefined) return ''
  return String(value)
}

function toControl(item) {
  if (item.valueType === 'BOOLEAN') return item.configValue === true || item.configValue === 'true'
  if (item.valueType === 'INT' || item.valueType === 'DECIMAL') return Number(item.configValue)
  return item.configValue ?? ''
}

function changedIn(group) {
  return group.items.filter(item => dirtyKeys.value.includes(item.configKey)).length
}

const gridRef = ref(null)
const twoColumns = ref(true)

const columns = computed(() => {
  const list = groups.value
  if (!twoColumns.value || list.length < 2) return [list]
  const tasks = list
    .map((group, index) => ({ group, index, weight: 52 + (group.items?.length || 0) * 104 }))
    .sort((a, b) => b.weight - a.weight || a.index - b.index)
  const buckets = [[], []]
  const load = [0, 0]
  tasks.forEach(task => {
    const target = load[0] <= load[1] ? 0 : 1
    buckets[target].push(task)
    load[target] += task.weight
  })
  return buckets.map(bucket => bucket.sort((a, b) => a.index - b.index).map(task => task.group))
})

function measure() {
  twoColumns.value = (gridRef.value?.clientWidth || window.innerWidth) >= 900
}

let resizeObserver

async function load() {
  loading.value = true
  try {
    const res = await getConfigGroups()
    groups.value = res.data || []
    const next = {}
    const baseline = {}
    groups.value.forEach(group => group.items.forEach(item => {
      next[item.configKey] = toControl(item)
      baseline[item.configKey] = normalize(next[item.configKey])
    }))
    values.value = next
    initial.value = baseline
  } finally {
    loading.value = false
  }
}

function validate() {
  if (!normalize(values.value['brand.platformName']).trim()) {
    ElMessage.warning('平台名称不能为空')
    return false
  }
  if (values.value['operations.maintenance.enabled']
    && !normalize(values.value['operations.maintenance.message']).trim()) {
    ElMessage.warning('开启维护模式时必须填写维护提示')
    return false
  }
  return true
}

async function save() {
  if (!hasChanges.value || !validate()) return
  const payload = {}
  dirtyKeys.value.forEach(key => {
    payload[key] = values.value[key]
  })
  saving.value = true
  try {
    await updateConfig(payload)
    ElMessage.success('配置已保存并生效')
    await load()
    await runtimeStore.refresh()
  } finally {
    saving.value = false
  }
}

const typeOptions = [
  { label: '字符串', value: 'STRING' },
  { label: '布尔', value: 'BOOLEAN' },
  { label: '整数', value: 'INT' },
  { label: '小数', value: 'DECIMAL' },
  { label: '枚举', value: 'ENUM' }
]

const itemDialog = reactive({ visible: false, editing: false, building: false })
const itemSaving = ref(false)
const itemFormRef = ref(null)
const itemForm = reactive({
  configKey: '',
  configName: '',
  configGroup: '',
  valueType: 'STRING',
  configValue: '',
  options: [],
  minValue: null,
  maxValue: null,
  maxLength: null,
  remark: ''
})

const itemRules = {
  configKey: [
    { required: true, message: '配置键不能为空', trigger: 'blur' },
    { pattern: /^ext\.[A-Za-z0-9_.]+$/, message: '以 ext. 开头，只允许字母、数字、下划线与点', trigger: 'blur' }
  ],
  configName: [{ required: true, message: '配置名称不能为空', trigger: 'blur' }],
  configGroup: [{ required: true, message: '请选择分组', trigger: 'change' }],
  valueType: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

watch(() => itemForm.valueType, (type, previous) => {
  if (itemDialog.building || !previous || type === previous) return
  if (type === 'ENUM' && !itemForm.options.length) {
    itemForm.options = [{ label: '', value: '', disabled: false }]
  }
  itemForm.configValue = defaultControlValue(type)
})

function defaultControlValue(type) {
  if (type === 'BOOLEAN') return false
  if (type === 'INT' || type === 'DECIMAL') return itemForm.minValue ?? 0
  if (type === 'ENUM') return itemForm.options[0]?.value ?? ''
  return ''
}

function resetDialogForm(editing) {
  itemDialog.editing = editing
  itemDialog.building = true
  itemDialog.visible = true
  nextTick(() => {
    itemDialog.building = false
    itemFormRef.value?.clearValidate()
  })
}

function openCreate(groupKey) {
  Object.assign(itemForm, {
    configKey: 'ext.',
    configName: '',
    configGroup: groupKey || groups.value[0]?.group || '',
    valueType: 'STRING',
    configValue: '',
    options: [],
    minValue: null,
    maxValue: null,
    maxLength: null,
    remark: ''
  })
  resetDialogForm(false)
}

function openEdit(item, groupKey) {
  Object.assign(itemForm, {
    configKey: item.configKey,
    configName: item.configName,
    configGroup: groupKey,
    valueType: item.valueType,
    configValue: toControl(item),
    options: (item.options || []).map(option => ({ ...option })),
    minValue: item.minValue ?? null,
    maxValue: item.maxValue ?? null,
    maxLength: item.maxLength ?? null,
    remark: item.remark || ''
  })
  resetDialogForm(true)
}

function buildItemPayload() {
  const numeric = itemForm.valueType === 'INT' || itemForm.valueType === 'DECIMAL'
  return {
    configName: itemForm.configName.trim(),
    configGroup: itemForm.configGroup,
    valueType: itemForm.valueType,
    configValue: String(itemForm.configValue ?? ''),
    options: itemForm.valueType === 'ENUM'
      ? itemForm.options.map(option => ({
        label: option.label.trim(),
        value: option.value.trim(),
        disabled: !!option.disabled
      }))
      : [],
    minValue: numeric ? itemForm.minValue ?? null : null,
    maxValue: numeric ? itemForm.maxValue ?? null : null,
    maxLength: itemForm.valueType === 'STRING' ? itemForm.maxLength ?? null : null,
    remark: itemForm.remark?.trim() || ''
  }
}

async function submitItem() {
  const valid = await itemFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (itemForm.valueType === 'ENUM') {
    const options = itemForm.options
    if (!options.length || options.some(option => !option.label?.trim() || !option.value?.trim())) {
      ElMessage.warning('枚举选项的显示名称与取值都不能为空')
      return
    }
    const enumValues = options.map(option => option.value.trim())
    if (new Set(enumValues).size !== enumValues.length) {
      ElMessage.warning('枚举取值不能重复')
      return
    }
    if (!enumValues.includes(String(itemForm.configValue))) {
      ElMessage.warning('当前值不在枚举选项中，请重新选择')
      return
    }
  }
  if ((itemForm.valueType === 'INT' || itemForm.valueType === 'DECIMAL')
    && itemForm.minValue != null && itemForm.maxValue != null
    && Number(itemForm.minValue) > Number(itemForm.maxValue)) {
    ElMessage.warning('数值下限不能大于上限')
    return
  }
  itemSaving.value = true
  try {
    if (itemDialog.editing) {
      await updateConfigItem(itemForm.configKey, buildItemPayload())
    } else {
      await createConfigItem({ ...buildItemPayload(), configKey: itemForm.configKey.trim() })
    }
    ElMessage.success(itemDialog.editing ? '配置项已更新' : '配置项已创建')
    itemDialog.visible = false
    await load()
  } finally {
    itemSaving.value = false
  }
}

async function removeItem(item) {
  try {
    await ElMessageBox.confirm(
      `删除扩展配置「${item.configName}」？依赖该键的读取将回退为默认值。`,
      '删除配置项',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  await deleteConfigItem(item.configKey)
  ElMessage.success('配置项已删除')
  await load()
}

onMounted(() => {
  load()
  measure()
  resizeObserver = new ResizeObserver(measure)
  if (gridRef.value) resizeObserver.observe(gridRef.value)
})

onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<style lang="scss" scoped>
.config-page {
  .scope-note {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    margin: 0 0 14px;
    padding: 12px 16px;
    border: 1px solid rgba(0, 168, 112, 0.16);
    border-radius: 12px;
    background: linear-gradient(120deg, rgba(0, 168, 112, 0.07), rgba(0, 168, 112, 0.025));

    .note-icon {
      flex: none;
      margin-top: 2px;
      color: var(--el-color-primary);
      font-size: 15px;
    }

    .note-title {
      margin: 0;
      color: #0b3e2e;
      font-size: 13px;
      font-weight: 600;
    }

    .note-desc {
      margin: 3px 0 0;
      font-size: 12.5px;
      line-height: 1.65;
      color: #5b6b64;
    }
  }

  .group-grid {
    display: flex;
    align-items: flex-start;
    gap: 14px;
  }

  .group-col {
    display: flex;
    flex: 1 1 0;
    flex-direction: column;
    gap: 14px;
    min-width: 0;
  }

  .group-card {
    border-radius: 14px;

    :deep(.el-card__header) {
      padding: 13px 18px;
      border-bottom: 1px solid #edf2ef;
      background: #fbfdfc;
    }

    :deep(.el-card__body) {
      padding: 4px 18px;
    }
  }

  .group-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .group-title {
    display: flex;
    align-items: baseline;
    gap: 9px;
    min-width: 0;
  }

  .group-name {
    font-size: 14px;
    font-weight: 700;
    letter-spacing: 0.04em;
    color: var(--bc-text, #101828);
  }

  .group-key {
    font-family: Consolas, Monaco, 'Courier New', monospace;
    font-size: 11px;
    color: #93a49b;
  }

  .dirty-tag {
    flex: none;
    padding: 2px 8px;
    border-radius: 999px;
    background: rgba(0, 168, 112, 0.1);
    font-size: 11px;
    color: #067a56;
  }

  .item-list {
    display: flex;
    flex-direction: column;
  }

  .config-item {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 18px;
    padding: 14px 0;
    border-bottom: 1px dashed #eef3f0;

    &:last-child {
      border-bottom: none;
    }
  }

  .item-meta {
    min-width: 0;
    flex: 1 1 auto;
  }

  .item-name {
    display: flex;
    align-items: center;
    gap: 7px;
    font-size: 13px;
    font-weight: 600;
    color: #253c33;
  }

  .item-remark {
    margin: 4px 0 0;
    font-size: 11.5px;
    line-height: 1.55;
    color: #7b8c84;
  }

  .item-key {
    display: inline-block;
    margin-top: 4px;
    font-family: Consolas, Monaco, 'Courier New', monospace;
    font-size: 10.5px;
    color: #a4b2ab;
  }

  .item-control {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 5px;
    flex: none;
  }

  .control-note {
    max-width: 260px;
    margin: 0;
    font-size: 10.5px;
    line-height: 1.5;
    text-align: right;
    color: #93a49b;
  }

  .item-tools {
    display: flex;
    align-items: center;
    gap: 6px;

    :deep(.el-button + .el-button) {
      margin-left: 0;
    }
  }

  .bool-switch {
    margin-top: 2px;
  }

  .enum-select {
    width: 230px;
  }

  .number-input {
    width: 150px;
  }

  .text-input {
    width: 280px;

    &.is-area {
      width: 380px;
    }
  }

  @media (max-width: 700px) {
    .config-item {
      flex-direction: column;
      gap: 8px;
    }

    .item-control {
      width: 100%;
      align-items: flex-start;
    }

    .enum-select,
    .number-input,
    .text-input,
    .text-input.is-area {
      width: 100%;
    }

    .control-note {
      max-width: none;
      text-align: left;
    }
  }
}

.item-form {
  .form-select,
  .form-number {
    width: 100%;
  }

  .form-tip {
    margin: 4px 0 0;
    font-size: 11.5px;
    line-height: 1.5;
    color: #93a49b;
  }

  .option-editor {
    width: 100%;
  }

  .option-row {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    margin-bottom: 8px;
  }
}
</style>

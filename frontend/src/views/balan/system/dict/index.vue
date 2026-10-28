<template>
  <div class="app-container dict-page">
    <div class="page-head">
      <div>
        <p class="page-kicker">SYSTEM · DICTIONARY</p>
        <h2>字典管理</h2>
        <p class="page-desc">维护平台枚举与展示标签，供后台筛选项、状态标签和后续配置复用。</p>
      </div>
      <div class="page-actions">
        <el-button icon="Refresh" @click="refreshAll">刷新</el-button>
        <el-button v-hasPermi="['system:dict:add']" type="primary" icon="Plus" @click="openTypeDialog()">新增字典</el-button>
      </div>
    </div>

    <el-row :gutter="14">
      <el-col :xs="24" :md="9">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-head">
              <span>字典类型</span>
              <el-tag size="small" type="info" effect="plain">{{ typeTotal }} 个</el-tag>
            </div>
          </template>
          <el-input v-model="typeKeyword" placeholder="搜索字典名称 / 类型" clearable class="panel-search" @keyup.enter="getTypes" @clear="getTypes">
            <template #append>
              <el-button icon="Search" @click="getTypes" />
            </template>
          </el-input>
          <el-table
            v-loading="typeLoading"
            :data="typeList"
            row-key="dictId"
            highlight-current-row
            :current-row-key="selectedType?.dictId"
            class="type-table"
            empty-text="暂无字典"
            @current-change="selectType"
          >
            <el-table-column label="字典名称" min-width="150">
              <template #default="{ row }">
                <div class="strong">{{ row.dictName }}</div>
                <div class="muted mono">{{ row.dictType }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="76" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === '0' ? 'success' : 'danger'" effect="plain">
                  {{ row.status === '0' ? '正常' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="110" align="right">
              <template #default="{ row }">
                <el-button v-hasPermi="['system:dict:edit']" link type="primary" icon="Edit" @click.stop="openTypeDialog(row)" />
                <el-button v-hasPermi="['system:dict:remove']" link type="danger" icon="Delete" @click.stop="handleDeleteType(row)" />
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="15">
        <el-card shadow="never" class="panel-card">
          <template #header>
            <div class="panel-head">
              <div>
                <span>字典数据</span>
                <span v-if="selectedType" class="muted selected-type">{{ selectedType.dictName }} · {{ selectedType.dictType }}</span>
              </div>
              <div class="panel-actions">
                <el-button
                  v-hasPermi="['system:dict:add']"
                  type="primary"
                  plain
                  size="small"
                  icon="Plus"
                  :disabled="!selectedType"
                  @click="openDataDialog()"
                >新增数据</el-button>
              </div>
            </div>
          </template>

          <template v-if="selectedType">
            <el-form :inline="true">
              <el-form-item label="关键词">
                <el-input v-model="dataQuery.keyword" placeholder="标签 / 键值" clearable style="width: 180px" @keyup.enter="getData" @clear="getData" />
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="dataQuery.status" placeholder="全部" clearable style="width: 110px" @change="getData">
                  <el-option label="正常" value="0" />
                  <el-option label="停用" value="1" />
                </el-select>
              </el-form-item>
              <el-form-item>
                <el-button type="primary" icon="Search" @click="getData">搜索</el-button>
              </el-form-item>
            </el-form>

            <el-table v-loading="dataLoading" :data="dataList" row-key="dictCode" empty-text="该字典暂无数据">
              <el-table-column label="排序" width="70" align="center">
                <template #default="{ row }">{{ row.dictSort }}</template>
              </el-table-column>
              <el-table-column label="标签" min-width="130">
                <template #default="{ row }">
                  <span class="strong">{{ row.dictLabel }}</span>
                  <el-tag v-if="row.isDefault === 'Y'" size="small" type="success" effect="plain" class="default-tag">默认</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="键值" min-width="130">
                <template #default="{ row }">
                  <span class="mono">{{ row.dictValue }}</span>
                </template>
              </el-table-column>
              <el-table-column label="样式" width="100" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.listClass" size="small" :type="tagType(row.listClass)" effect="plain">
                    {{ row.listClass }}
                  </el-tag>
                  <span v-else class="muted">-</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === '0' ? 'success' : 'danger'" effect="plain">
                    {{ row.status === '0' ? '正常' : '停用' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="备注" min-width="130">
                <template #default="{ row }">
                  <span class="muted">{{ row.remark || '-' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="130" fixed="right">
                <template #default="{ row }">
                  <el-button v-hasPermi="['system:dict:edit']" link type="primary" icon="Edit" @click="openDataDialog(row)">编辑</el-button>
                  <el-button v-hasPermi="['system:dict:remove']" link type="danger" icon="Delete" @click="handleDeleteData(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>

            <pagination
              v-show="dataTotal > 0"
              v-model:page="dataQuery.pageNum"
              v-model:limit="dataQuery.pageSize"
              :total="dataTotal"
              @pagination="getData"
            />
          </template>
          <el-empty v-else description="请先在左侧选择一个字典类型" />
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="typeDialogVisible" :title="typeForm.dictId ? '编辑字典类型' : '新增字典类型'" width="460px" :close-on-click-modal="false">
      <el-form ref="typeFormRef" :model="typeForm" :rules="typeRules" label-width="90px">
        <el-form-item label="字典名称" prop="dictName">
          <el-input v-model="typeForm.dictName" maxlength="100" placeholder="例如 订单状态" />
        </el-form-item>
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="typeForm.dictType" maxlength="100" placeholder="例如 order_status" :disabled="!!typeForm.dictId" />
          <div v-if="typeForm.dictId" class="form-tip">字典类型作为键值，创建后不可修改。</div>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="typeForm.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="typeForm.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitType">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="dataDialogVisible" :title="dataForm.dictCode ? '编辑字典数据' : '新增字典数据'" width="480px" :close-on-click-modal="false">
      <el-form ref="dataFormRef" :model="dataForm" :rules="dataRules" label-width="90px">
        <el-form-item label="所属字典">
          <el-input :model-value="selectedType ? `${selectedType.dictName} (${selectedType.dictType})` : ''" disabled />
        </el-form-item>
        <el-form-item label="标签" prop="dictLabel">
          <el-input v-model="dataForm.dictLabel" maxlength="100" placeholder="展示文案，例如 充电中" />
        </el-form-item>
        <el-form-item label="键值" prop="dictValue">
          <el-input v-model="dataForm.dictValue" maxlength="100" placeholder="程序值，例如 CHARGING" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dataForm.dictSort" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="标签样式">
          <el-select v-model="dataForm.listClass" placeholder="默认" clearable style="width: 100%">
            <el-option v-for="item in tagOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认项">
          <el-switch v-model="dataForm.isDefault" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="dataForm.status">
            <el-radio label="0">正常</el-radio>
            <el-radio label="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dataForm.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dataDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitData">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addDictData,
  addDictType,
  deleteDictData,
  deleteDictType,
  listDictData,
  listDictType,
  updateDictData,
  updateDictType
} from '@/api/balan/dict'

defineOptions({ name: 'BalanDict' })

const tagOptions = [
  { value: 'success', label: 'success（绿色）' },
  { value: 'warning', label: 'warning（橙色）' },
  { value: 'danger', label: 'danger（红色）' },
  { value: 'info', label: 'info（灰色）' },
  { value: 'primary', label: 'primary（蓝色）' }
]

const typeLoading = ref(false)
const typeList = ref([])
const typeTotal = ref(0)
const typeKeyword = ref('')
const selectedType = ref(null)

const dataLoading = ref(false)
const dataList = ref([])
const dataTotal = ref(0)
const dataQuery = reactive({
  pageNum: 1,
  pageSize: 10,
  keyword: undefined,
  status: undefined
})

const submitLoading = ref(false)

const typeDialogVisible = ref(false)
const typeFormRef = ref()
const typeForm = reactive({
  dictId: undefined,
  dictName: '',
  dictType: '',
  status: '0',
  remark: ''
})
const typeRules = {
  dictName: [{ required: true, message: '请输入字典名称', trigger: 'blur' }],
  dictType: [{ required: true, message: '请输入字典类型', trigger: 'blur' }]
}

const dataDialogVisible = ref(false)
const dataFormRef = ref()
const dataForm = reactive({
  dictCode: undefined,
  dictLabel: '',
  dictValue: '',
  dictSort: 0,
  listClass: '',
  isDefault: false,
  status: '0',
  remark: ''
})
const dataRules = {
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典键值', trigger: 'blur' }]
}

function getTypes() {
  typeLoading.value = true
  return listDictType({ pageNum: 1, pageSize: 100, keyword: typeKeyword.value || undefined }).then(res => {
    typeList.value = res.rows || []
    typeTotal.value = res.total || 0
    if (!selectedType.value && typeList.value.length > 0) {
      selectType(typeList.value[0])
    } else if (selectedType.value) {
      const current = typeList.value.find(item => item.dictId === selectedType.value.dictId)
      if (current) {
        selectedType.value = current
      }
    }
  }).finally(() => {
    typeLoading.value = false
  })
}

function selectType(row) {
  if (!row) {
    return
  }
  selectedType.value = row
  dataQuery.pageNum = 1
  dataQuery.keyword = undefined
  dataQuery.status = undefined
  getData()
}

function getData() {
  if (!selectedType.value) {
    return
  }
  dataLoading.value = true
  listDictData({
    ...dataQuery,
    dictType: selectedType.value.dictType
  }).then(res => {
    dataList.value = res.rows || []
    dataTotal.value = res.total || 0
  }).finally(() => {
    dataLoading.value = false
  })
}

function refreshAll() {
  return getTypes()
}

function openTypeDialog(row) {
  typeForm.dictId = row?.dictId
  typeForm.dictName = row?.dictName || ''
  typeForm.dictType = row?.dictType || ''
  typeForm.status = row?.status || '0'
  typeForm.remark = row?.remark || ''
  typeDialogVisible.value = true
  typeFormRef.value?.clearValidate()
}

function submitType() {
  typeFormRef.value?.validate(valid => {
    if (!valid) {
      return
    }
    submitLoading.value = true
    const payload = {
      dictName: typeForm.dictName,
      dictType: typeForm.dictType,
      status: typeForm.status,
      remark: typeForm.remark
    }
    const request = typeForm.dictId ? updateDictType(typeForm.dictId, payload) : addDictType(payload)
    request.then(() => {
      ElMessage.success(typeForm.dictId ? '字典类型已更新' : '字典类型已新增')
      typeDialogVisible.value = false
      getTypes()
    }).finally(() => {
      submitLoading.value = false
    })
  })
}

function handleDeleteType(row) {
  ElMessageBox.confirm(`确认删除字典「${row.dictName}」吗？需先删除其下所有数据项。`, '删除字典', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(() => {
    return deleteDictType(row.dictId).then(() => {
      ElMessage.success('字典已删除')
      if (selectedType.value?.dictId === row.dictId) {
        selectedType.value = null
        dataList.value = []
        dataTotal.value = 0
      }
      getTypes()
    })
  }).catch(() => {})
}

function openDataDialog(row) {
  if (!selectedType.value) {
    return
  }
  dataForm.dictCode = row?.dictCode
  dataForm.dictLabel = row?.dictLabel || ''
  dataForm.dictValue = row?.dictValue || ''
  dataForm.dictSort = row?.dictSort ?? 0
  dataForm.listClass = row?.listClass || ''
  dataForm.isDefault = row?.isDefault === 'Y'
  dataForm.status = row?.status || '0'
  dataForm.remark = row?.remark || ''
  dataDialogVisible.value = true
  dataFormRef.value?.clearValidate()
}

function submitData() {
  dataFormRef.value?.validate(valid => {
    if (!valid || !selectedType.value) {
      return
    }
    submitLoading.value = true
    const payload = {
      dictLabel: dataForm.dictLabel,
      dictValue: dataForm.dictValue,
      dictType: selectedType.value.dictType,
      dictSort: dataForm.dictSort,
      listClass: dataForm.listClass,
      isDefault: dataForm.isDefault ? 'Y' : 'N',
      status: dataForm.status,
      remark: dataForm.remark
    }
    const request = dataForm.dictCode ? updateDictData(dataForm.dictCode, payload) : addDictData(payload)
    request.then(() => {
      ElMessage.success(dataForm.dictCode ? '字典数据已更新' : '字典数据已新增')
      dataDialogVisible.value = false
      getData()
    }).finally(() => {
      submitLoading.value = false
    })
  })
}

function handleDeleteData(row) {
  ElMessageBox.confirm(`确认删除字典数据「${row.dictLabel}」吗？`, '删除字典数据', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(() => {
    return deleteDictData(row.dictCode).then(() => {
      ElMessage.success('字典数据已删除')
      getData()
    })
  }).catch(() => {})
}

function tagType(listClass) {
  return ['success', 'warning', 'danger', 'info', 'primary'].includes(listClass) ? listClass : 'info'
}

onMounted(getTypes)
</script>

<style scoped lang="scss">
.dict-page {
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

  .panel-card {
    border: 1px solid #e2e8f0;
    border-radius: 12px;
    margin-bottom: 14px;
  }

  .panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    color: #0f172a;
    font-weight: 600;
  }

  .selected-type {
    margin-left: 8px;
    font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
    font-weight: 400;
  }

  .panel-search {
    margin-bottom: 12px;
  }

  .strong {
    color: #0f172a;
    font-weight: 600;
  }

  .muted {
    color: #94a3b8;
    font-size: 12px;
  }

  .mono {
    font-family: 'JetBrains Mono', 'Cascadia Mono', monospace;
    font-size: 12px;
  }

  .default-tag {
    margin-left: 6px;
  }

  .form-tip {
    margin-top: 4px;
    color: #94a3b8;
    font-size: 12px;
    line-height: 1.5;
  }

  :deep(.type-table .el-table__row) {
    cursor: pointer;
  }
}
</style>

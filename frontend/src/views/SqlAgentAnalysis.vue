<template>
  <div class="sql-agent-analysis">
    <el-card>
      <template #header>
        <div class="header">
          <h1>🤖 SQL Agent 智能分析</h1>
          <p>基于链式工作流的 SQL 性能智能分析系统</p>
        </div>
      </template>

      <!-- 配置区域 -->
      <el-card style="margin-bottom: 20px">
        <el-form :model="form" label-width="120px" inline>
          <el-form-item label="Namespace">
            <el-select
              v-model="form.namespace"
              placeholder="请选择或输入 Namespace"
              filterable
              allow-create
              default-first-option
              style="width: 400px"
              @change="handleNamespaceChange"
              @keyup.enter="handleNamespaceEnter"
              :loading="loadingNamespaces || parsingNamespace"
            >
              <el-option
                v-for="ns in namespaces"
                :key="ns"
                :label="ns"
                :value="ns"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="数据源">
            <el-select
              v-model="form.datasource"
              placeholder="选择数据源"
              style="width: 200px"
              :disabled="!form.namespace"
              @change="handleDatasourceOrLlmChange"
            >
              <el-option
                v-for="ds in datasources"
                :key="ds.name"
                :label="ds.name"
                :value="ds.name"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="LLM 模型">
            <el-select
              v-model="form.llm"
              placeholder="选择 LLM 模型"
              style="width: 250px"
              :disabled="!form.namespace"
              @change="handleDatasourceOrLlmChange"
            >
              <el-option
                v-for="llm in llms"
                :key="llm.name"
                :label="`${llm.name} (${llm.model})`"
                :value="llm.name"
              />
            </el-select>
          </el-form-item>

          <el-form-item>
            <el-button
              type="primary"
              @click="handleAnalyze"
              :loading="analyzing"
              :disabled="selectedQueryIds.length === 0 || !form.datasource || !form.llm"
            >
              {{ analyzing ? '分析中...' : `分析选中 SQL (${selectedQueryIds.length})` }}
            </el-button>
            <el-button @click="handleClear">清空</el-button>
          </el-form-item>
        </el-form>

        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          :closable="false"
          show-icon
          style="margin-top: 20px"
        />
        <el-alert
          v-if="parsingNamespace"
          title="正在解析Namespace..."
          type="info"
          :closable="false"
          show-icon
          style="margin-top: 20px"
        />
      </el-card>

      <!-- 参数编辑卡片（当需要编辑参数时显示） -->
      <el-card v-if="form.namespace && needEditParameters" style="margin-bottom: 20px">
        <template #header>
          <div class="card-header">
            <span>Mapper参数列表</span>
          </div>
        </template>

        <el-alert
          :title="`当前命名空间: ${form.namespace} - 请先编辑并保存Mapper参数，然后继续解析SQL`"
          type="warning"
          :closable="false"
          style="margin-bottom: 20px"
          show-icon
        />

        <div style="margin-bottom: 10px">
          <el-button
            type="primary"
            @click="handleSaveParameters"
            :loading="savingParameters"
            :disabled="Object.keys(editingParameters).length === 0"
          >
            保存修改 ({{ Object.keys(editingParameters).length }})
          </el-button>
          <el-button
            type="danger"
            @click="handleDeleteParameters"
            :loading="deletingParameters"
            :disabled="selectedParameterIds.length === 0"
          >
            删除选中 ({{ selectedParameterIds.length }})
          </el-button>
        </div>

        <el-table
          v-loading="loadingParameters"
          :data="parameters"
          border
          stripe
          style="width: 100%"
          :empty-text="'该命名空间下暂无参数'"
          @selection-change="handleParameterSelectionChange"
        >
          <el-table-column type="selection" width="55" />
          <el-table-column prop="mapperId" label="Mapper ID" width="300">
            <template #default="{ row }">
              <el-input
                v-if="editingParameters[row.id]"
                v-model="editingParameters[row.id].mapperId"
                size="small"
              />
              <span v-else>{{ row.mapperId }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="parameterName" label="参数名" width="200">
            <template #default="{ row }">
              <el-input
                v-if="editingParameters[row.id]"
                v-model="editingParameters[row.id].parameterName"
                size="small"
              />
              <span v-else>{{ row.parameterName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="parameterValue" label="参数值" min-width="300">
            <template #default="{ row }">
              <el-input
                v-if="editingParameters[row.id]"
                v-model="editingParameters[row.id].parameterValue"
                type="textarea"
                :rows="2"
                size="small"
              />
              <span v-else style="white-space: pre-wrap">{{ row.parameterValue }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="testExpression" label="Test表达式" width="250">
            <template #default="{ row }">
              <el-input
                v-if="editingParameters[row.id]"
                v-model="editingParameters[row.id].testExpression"
                size="small"
                placeholder="OGNL表达式"
              />
              <span v-else>{{ row.testExpression || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="180">
            <template #default="{ row }">
              {{ formatDateTime(row.updatedAt) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="!editingParameters[row.id]"
                type="primary"
                link
                size="small"
                @click="startEditParameter(row)"
              >
                编辑
              </el-button>
              <el-button
                v-else
                type="success"
                link
                size="small"
                @click="cancelEditParameter(row.id)"
              >
                取消
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- SQL 列表 -->
      <el-card v-if="form.namespace && !needEditParameters">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span>SQL 列表 ({{ queries.length }})</span>
            <div>
              <el-button size="small" @click="handleSelectAll">全选</el-button>
              <el-button size="small" @click="handleSelectNone">取消全选</el-button>
              <el-button size="small" type="info" @click="handleShowParameters">查看参数</el-button>
              <el-button size="small" type="warning" @click="handleRefreshQueries">刷新解析</el-button>
            </div>
          </div>
        </template>

        <el-table
          ref="queryTableRef"
          v-loading="loadingQueries"
          :data="queries"
          @selection-change="handleSelectionChange"
          border
          stripe
          max-height="500"
        >
          <el-table-column type="selection" width="55" />
          <el-table-column type="expand" width="50">
            <template #default="{ row }">
              <div class="filling-results-container">
                <div v-if="loadingFillingRecords" class="loading-container">
                  <el-icon class="is-loading"><Loading /></el-icon>
                  <span>加载填充记录中...</span>
                </div>
                <div v-else-if="getFillingRecord(row)" class="filling-scenarios">
                  <div
                    v-for="(scenario, index) in getFillingRecord(row).scenarios"
                    :key="index"
                    class="scenario-item"
                  >
                    <div class="scenario-header">
                      <el-tag type="info" size="small">{{ scenario.scenarioName }}</el-tag>
                    </div>
                    <div class="scenario-sql">
                      <pre class="filled-sql">{{ scenario.filledSql }}</pre>
                    </div>
                  </div>
                </div>
                <div v-else class="no-filling-record">
                  <el-empty description="暂无填充记录" :image-size="80" />
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="statementId" label="Statement ID" width="200" />
          <el-table-column prop="queryType" label="类型" width="100">
            <template #default="{ row }">
              <el-tag :type="getQueryTypeTag(row.queryType)" size="small">
                {{ row.queryType || 'N/A' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="sql" label="SQL 语句" min-width="300" show-overflow-tooltip>
            <template #default="{ row }">
              <pre class="sql-preview">{{ row.sql }}</pre>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="queries.length === 0 && !loadingQueries" style="text-align: center; padding: 40px; color: #999">
          该 Namespace 下暂无 SQL 查询
        </div>
      </el-card>

      <!-- 分析结果 -->
      <!-- 索引优化报告 -->
      <IndexOptimizationReportViewer 
        v-if="analysisResult"
        :report-content="analysisResult"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  getDatasources, 
  getLlms, 
  getNamespaces, 
  getQueriesByNamespace, 
  analyzeAgent, 
  getFillingRecords,
  parseByNamespace,
  refreshByNamespace,
  getParametersByNamespace,
  updateParameter,
  deleteParameters
} from '@/api'
import IndexOptimizationReportViewer from '@/components/sql-agent/IndexOptimizationReportViewer.vue'

const queryTableRef = ref(null)
const loadingNamespaces = ref(false)
const loadingQueries = ref(false)
const analyzing = ref(false)
const errorMessage = ref('')
const namespaces = ref([])
const queries = ref([])
const datasources = ref([])
const llms = ref([])
const selectedQueryIds = ref([])
const analysisResult = ref(null)
const fillingRecords = ref({}) // key: mapperId, value: 填充记录数据
const loadingFillingRecords = ref(false)

// 参数编辑相关状态
const needEditParameters = ref(false) // 是否需要编辑参数
const parameters = ref([]) // 参数列表
const editingParameters = ref({}) // 正在编辑的参数
const selectedParameterIds = ref([]) // 选中的参数ID
const loadingParameters = ref(false) // 加载参数状态
const savingParameters = ref(false) // 保存参数状态
const deletingParameters = ref(false) // 删除参数状态
const parsingNamespace = ref(false) // 解析namespace状态

const form = reactive({
  namespace: '',
  datasource: '',
  llm: ''
})

onMounted(async () => {
  await Promise.all([
    loadNamespaces(),
    loadDatasources(),
    loadLlms()
  ])
})

async function loadNamespaces() {
  loadingNamespaces.value = true
  try {
    namespaces.value = await getNamespaces()
  } catch (error) {
    console.error('加载 Namespace 列表失败:', error)
    errorMessage.value = '加载 Namespace 列表失败: ' + error.message
  } finally {
    loadingNamespaces.value = false
  }
}

async function loadDatasources() {
  try {
    datasources.value = await getDatasources()
  } catch (error) {
    console.error('加载数据源失败:', error)
  }
}

async function loadLlms() {
  try {
    llms.value = await getLlms()
  } catch (error) {
    console.error('加载 LLM 列表失败:', error)
  }
}

async function handleNamespaceEnter() {
  // 当用户按回车键时，如果输入的是新 namespace，则触发解析
  if (!form.namespace || !form.namespace.trim()) {
    return
  }

  const namespace = form.namespace.trim()
  
  // 检查是否是新增的 namespace
  const isNewNamespace = !namespaces.value.includes(namespace)
  
  if (isNewNamespace) {
    // 如果是新增的 namespace，触发解析
    await handleNamespaceChange()
  }
}

async function handleNamespaceChange() {
  if (!form.namespace) {
    queries.value = []
    selectedQueryIds.value = []
    analysisResult.value = null
    needEditParameters.value = false
    parameters.value = []
    if (queryTableRef.value) {
      queryTableRef.value.clearSelection()
    }
    return
  }

  // 去除首尾空格并更新 form.namespace
  const namespace = form.namespace.trim()
  if (namespace !== form.namespace) {
    form.namespace = namespace
  }
  
  const isNewNamespace = !namespaces.value.includes(namespace)

  parsingNamespace.value = true
  errorMessage.value = ''
  queries.value = []
  selectedQueryIds.value = []
  analysisResult.value = null
  needEditParameters.value = false
  parameters.value = []
  if (queryTableRef.value) {
    queryTableRef.value.clearSelection()
  }

  try {
    // 1. 调用新API: parseByNamespace
    const parseResult = await parseByNamespace(namespace)
    
    // 2. 检查响应中的 needEdit 标志
    if (parseResult.needEdit === true) {
      // 需要编辑参数
      needEditParameters.value = true
      parameters.value = parseResult.parameters || []
      ElMessage.info('请先编辑并保存Mapper参数，然后继续解析SQL')
    } else {
      // 参数已存在，直接加载SQL列表
      needEditParameters.value = false
      queries.value = parseResult.queries || []
      
      // 如果已选择数据源和LLM，加载填充记录
      if (form.datasource && form.llm) {
        await loadFillingRecords()
      }
    }

    // 3. 如果是新增的 namespace 且解析成功，将其添加到列表中
    if (isNewNamespace && namespace && !namespaces.value.includes(namespace)) {
      namespaces.value.push(namespace)
      namespaces.value.sort() // 保持列表有序
    }
  } catch (error) {
    console.error('解析namespace失败:', error)
    errorMessage.value = '解析namespace失败: ' + error.message
    
    // 如果解析失败，尝试直接加载查询列表（兼容旧逻辑）
    try {
      loadingQueries.value = true
      queries.value = await getQueriesByNamespace(namespace)
      needEditParameters.value = false
      
      // 即使解析失败，如果成功加载了查询列表，也添加到 namespace 列表
      if (isNewNamespace && namespace && !namespaces.value.includes(namespace)) {
        namespaces.value.push(namespace)
        namespaces.value.sort()
      }
    } catch (loadError) {
      console.error('加载 SQL 列表失败:', loadError)
      errorMessage.value = '加载 SQL 列表失败: ' + loadError.message
    } finally {
      loadingQueries.value = false
    }
  } finally {
    parsingNamespace.value = false
  }
}

async function handleDatasourceOrLlmChange() {
  if (form.namespace && form.datasource && form.llm && queries.value.length > 0) {
    await loadFillingRecords()
  } else {
    fillingRecords.value = {}
  }
}

async function loadFillingRecords() {
  if (!form.namespace || !form.datasource || !form.llm || queries.value.length === 0) {
    return
  }

  loadingFillingRecords.value = true
  try {
    const mapperIds = queries.value.map(query => `${form.namespace}.${query.statementId}`)
    const response = await getFillingRecords(mapperIds, form.datasource, form.llm)
    fillingRecords.value = response.records || {}
  } catch (error) {
    console.error('加载填充记录失败:', error)
    fillingRecords.value = {}
  } finally {
    loadingFillingRecords.value = false
  }
}

function getFillingRecord(row) {
  const mapperId = `${form.namespace}.${row.statementId}`
  return fillingRecords.value[mapperId] || null
}

function handleSelectionChange(selection) {
  selectedQueryIds.value = selection.map(item => item.id)
}

function handleSelectAll() {
  if (queryTableRef.value) {
    queries.value.forEach(query => {
      queryTableRef.value.toggleRowSelection(query, true)
    })
  }
}

function handleSelectNone() {
  if (queryTableRef.value) {
    queryTableRef.value.clearSelection()
  }
}

async function handleAnalyze() {
  if (selectedQueryIds.value.length === 0) {
    errorMessage.value = '请至少选择一个 SQL 进行分析'
    return
  }

  if (!form.datasource || !form.llm) {
    errorMessage.value = '请选择数据源和 LLM 模型'
    return
  }

  analyzing.value = true
  errorMessage.value = ''
  analysisResult.value = null

  try {
    // 获取选中的 SQL 查询
    const selectedQueries = queries.value.filter(q => selectedQueryIds.value.includes(q.id))
    
    if (selectedQueries.length === 0) {
      throw new Error('未找到选中的 SQL 查询')
    }

    // 构建多 SQL 请求
    const sqlItems = selectedQueries.map(query => ({
      sql: query.sql,
      mapperId: `${form.namespace}.${query.statementId}`
    }))

    // 发送多 SQL 分析请求
    const response = await analyzeAgent({
      sqlItems: sqlItems,
      datasourceName: form.datasource,
      llmName: form.llm
    })

    // 处理响应 - 直接使用 reportContent
    if (response.reportContent) {
      analysisResult.value = response.reportContent
    } else {
      throw new Error('未收到分析报告')
    }
  } catch (error) {
    errorMessage.value = '分析失败: ' + error.message
    console.error('分析错误:', error)
  } finally {
    analyzing.value = false
  }
}

function handleClear() {
  form.namespace = ''
  form.datasource = ''
  form.llm = ''
  queries.value = []
  selectedQueryIds.value = []
  analysisResult.value = null
  errorMessage.value = ''
  fillingRecords.value = {}
  needEditParameters.value = false
  parameters.value = []
  editingParameters.value = {}
  selectedParameterIds.value = []
  if (queryTableRef.value) {
    queryTableRef.value.clearSelection()
  }
}

function getQueryTypeTag(type) {
  const typeMap = {
    SELECT: 'success',
    INSERT: 'primary',
    UPDATE: 'warning',
    DELETE: 'danger'
  }
  return typeMap[type] || 'info'
}

// 参数编辑相关方法（复用MapperUpload的逻辑）
function startEditParameter(row) {
  editingParameters.value[row.id] = {
    mapperId: row.mapperId,
    parameterName: row.parameterName,
    parameterValue: row.parameterValue,
    testExpression: row.testExpression || ''
  }
}

function cancelEditParameter(id) {
  delete editingParameters.value[id]
}

function handleParameterSelectionChange(selection) {
  selectedParameterIds.value = selection.map(item => item.id)
}

async function handleSaveParameters() {
  const editingIds = Object.keys(editingParameters.value)
  if (editingIds.length === 0) {
    ElMessage.warning('没有需要保存的修改')
    return
  }

  savingParameters.value = true
  try {
    // 1. 保存参数（复用MapperUpload的逻辑）
    const promises = editingIds.map(id => {
      const editData = editingParameters.value[id]
      return updateParameter(parseInt(id), editData)
    })

    await Promise.all(promises)
    ElMessage.success(`成功保存 ${editingIds.length} 个参数`)
    
    // 2. 清空编辑状态
    editingParameters.value = {}
    
    // 3. 触发SQL解析
    ElMessage.info('参数已保存，正在解析SQL...')
    parsingNamespace.value = true
    try {
      const parseResult = await parseByNamespace(form.namespace)
      
      if (parseResult.needEdit === true) {
        // 如果仍然需要编辑，刷新参数列表
        parameters.value = parseResult.parameters || []
        ElMessage.warning('仍有参数需要编辑')
      } else {
        // 解析成功，切换到SQL列表
        needEditParameters.value = false
        queries.value = parseResult.queries || []
        ElMessage.success('SQL解析完成')
        
        // 如果已选择数据源和LLM，加载填充记录
        if (form.datasource && form.llm) {
          await loadFillingRecords()
        }
      }
    } catch (parseError) {
      console.error('解析SQL失败:', parseError)
      ElMessage.error('解析SQL失败: ' + parseError.message)
    } finally {
      parsingNamespace.value = false
    }
    
    // 4. 刷新参数列表
    if (form.namespace) {
      await loadParameters()
    }
  } catch (err) {
    ElMessage.error('保存失败: ' + (err.message || '未知错误'))
    console.error('保存参数失败:', err)
  } finally {
    savingParameters.value = false
  }
}

async function handleDeleteParameters() {
  if (selectedParameterIds.value.length === 0) {
    ElMessage.warning('请选择要删除的参数')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedParameterIds.value.length} 个参数吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    deletingParameters.value = true
    try {
      await deleteParameters(selectedParameterIds.value)
      ElMessage.success(`成功删除 ${selectedParameterIds.value.length} 个参数`)
      
      // 清空选中和编辑状态
      selectedParameterIds.value = []
      editingParameters.value = {}
      
      // 刷新列表
      if (form.namespace) {
        await loadParameters()
      }
    } catch (err) {
      ElMessage.error('删除失败: ' + (err.message || '未知错误'))
      console.error('删除参数失败:', err)
    } finally {
      deletingParameters.value = false
    }
  } catch {
    // 用户取消删除
  }
}

async function loadParameters() {
  if (!form.namespace) {
    return
  }

  loadingParameters.value = true
  try {
    parameters.value = await getParametersByNamespace(form.namespace)
    editingParameters.value = {}
    selectedParameterIds.value = []
  } catch (err) {
    console.error('加载参数列表失败:', err)
    ElMessage.error('加载参数列表失败: ' + (err.message || '未知错误'))
  } finally {
    loadingParameters.value = false
  }
}

async function handleShowParameters() {
  needEditParameters.value = true
  await loadParameters()
}

async function handleRefreshQueries() {
  if (!form.namespace) {
    return
  }

  loadingQueries.value = true
  try {
    const result = await refreshByNamespace(form.namespace)
    if (result.success) {
      queries.value = result.queries || []
      ElMessage.success('SQL解析结果已刷新')
      
      // 如果已选择数据源和LLM，加载填充记录
      if (form.datasource && form.llm) {
        await loadFillingRecords()
      }
    } else {
      ElMessage.error(result.error || '刷新失败')
    }
  } catch (error) {
    console.error('刷新SQL解析结果失败:', error)
    ElMessage.error('刷新失败: ' + error.message)
  } finally {
    loadingQueries.value = false
  }
}

// 格式化日期时间
function formatDateTime(dateTime) {
  if (!dateTime) return '-'
  try {
    const date = new Date(dateTime)
    return date.toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    })
  } catch (e) {
    return dateTime
  }
}
</script>

<style scoped>
.sql-agent-analysis {
  width: 100%;
}

.header {
  text-align: center;
}

.header h1 {
  color: #333;
  margin-bottom: 10px;
  font-size: 28px;
}

.header p {
  color: #666;
  font-size: 16px;
}

.sql-preview {
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 100px;
  overflow: hidden;
}

.filling-results-container {
  padding: 20px;
  background-color: #f5f7fa;
}

.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: #909399;
}

.loading-container .el-icon {
  margin-right: 8px;
  font-size: 18px;
}

.filling-scenarios {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.scenario-item {
  background: white;
  border-radius: 4px;
  padding: 16px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.scenario-header {
  margin-bottom: 12px;
}

.scenario-sql {
  margin-top: 8px;
}

.filled-sql {
  margin: 0;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  background-color: #f8f9fa;
  padding: 12px;
  border-radius: 4px;
  border: 1px solid #e4e7ed;
  max-height: 200px;
  overflow-y: auto;
}

.no-filling-record {
  padding: 40px;
  text-align: center;
}

.card-header {
  font-weight: 600;
  font-size: 18px;
}
</style>



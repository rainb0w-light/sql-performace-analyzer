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
              placeholder="请选择 Namespace"
              filterable
              style="width: 400px"
              @change="handleNamespaceChange"
              :loading="loadingNamespaces"
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
      </el-card>

      <!-- SQL 列表 -->
      <el-card v-if="form.namespace">
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span>SQL 列表 ({{ queries.length }})</span>
            <div>
              <el-button size="small" @click="handleSelectAll">全选</el-button>
              <el-button size="small" @click="handleSelectNone">取消全选</el-button>
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
      <ResultViewer v-if="analysisResult" :result="analysisResult" :is-mapper="isBatchAnalysis" />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getDatasources, getLlms, getNamespaces, getQueriesByNamespace, analyzeAgent } from '@/api'
import ResultViewer from '@/components/sql-agent/ResultViewer.vue'

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
const isBatchAnalysis = ref(false)

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

async function handleNamespaceChange() {
  if (!form.namespace) {
    queries.value = []
    selectedQueryIds.value = []
    analysisResult.value = null
    if (queryTableRef.value) {
      queryTableRef.value.clearSelection()
    }
    return
  }

  loadingQueries.value = true
  errorMessage.value = ''
  queries.value = []
  selectedQueryIds.value = []
  analysisResult.value = null
  if (queryTableRef.value) {
    queryTableRef.value.clearSelection()
  }

  try {
    queries.value = await getQueriesByNamespace(form.namespace)
  } catch (error) {
    console.error('加载 SQL 列表失败:', error)
    errorMessage.value = '加载 SQL 列表失败: ' + error.message
  } finally {
    loadingQueries.value = false
  }
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

    // 判断是单个还是批量分析
    isBatchAnalysis.value = selectedQueries.length > 1

    if (selectedQueries.length === 1) {
      // 单条 SQL 分析
      const query = selectedQueries[0]
      const result = await analyzeAgent({
        sql: query.sql,
        datasourceName: form.datasource,
        llmName: form.llm
      })
      analysisResult.value = result
    } else {
      // 批量分析：构建类似 Mapper 批量分析的结果结构
      const results = []
      for (const query of selectedQueries) {
        try {
          const result = await analyzeAgent({
            sql: query.sql,
            datasourceName: form.datasource,
            llmName: form.llm
          })
          results.push(result)
        } catch (error) {
          console.error(`分析 SQL ${query.id} 失败:`, error)
          // 创建一个错误结果
          results.push({
            sql: query.sql,
            error: error.message || '分析失败',
            finalRiskLevel: 'UNKNOWN'
          })
        }
      }

      // 构建批量分析结果结构
      analysisResult.value = {
        mapperNamespace: form.namespace,
        results: results,
        overallSummary: `共分析了 ${results.length} 条 SQL 语句，成功 ${results.filter(r => !r.error).length} 条`
      }
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
</style>



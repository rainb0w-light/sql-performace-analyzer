<template>
  <div class="mapper-upload-container">
    <!-- 第一部分：上传功能 -->
    <el-card style="margin-bottom: 20px">
      <template #header>
        <div class="card-header">
          <span>上传MyBatis Mapper XML文件</span>
        </div>
      </template>

      <el-form :model="form" label-width="120px">
        <el-form-item label="Mapper命名空间：">
          <el-input
            v-model="form.mapperNamespace"
            placeholder="例如: com.example.mapper.UserMapper"
            :disabled="uploading"
          />
        </el-form-item>

        <el-form-item label="XML内容：">
          <el-input
            v-model="form.xmlContent"
            type="textarea"
            :rows="10"
            placeholder="粘贴MyBatis Mapper XML内容..."
            :disabled="uploading"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            @click="handleUpload"
            :loading="uploading"
            :disabled="!form.mapperNamespace.trim() && !form.xmlContent.trim()"
          >
            {{ uploading ? '解析中...' : '解析' }}
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
        style="margin-top: 20px"
      />

      <el-alert
        v-if="success"
        :title="success"
        type="success"
        :closable="false"
        show-icon
        style="margin-top: 20px"
      />
    </el-card>

    <!-- 第二部分：Namespace 选择和查询列表 -->
    <el-card style="margin-bottom: 20px">
      <template #header>
        <div class="card-header">
          <span>已解析的SQL查询</span>
        </div>
      </template>

      <el-form :inline="true" style="margin-bottom: 20px">
        <el-form-item label="选择命名空间：">
          <el-select
            v-model="selectedNamespace"
            placeholder="请选择命名空间"
            filterable
            clearable
            style="width: 400px"
            @change="handleNamespaceChange"
          >
            <el-option
              v-for="ns in namespaces"
              :key="ns"
              :label="ns"
              :value="ns"
            />
          </el-select>
          <el-button
            type="primary"
            style="margin-left: 10px"
            @click="loadNamespaces"
            :loading="loadingNamespaces"
          >
            刷新列表
          </el-button>
        </el-form-item>
      </el-form>

      <div style="margin-bottom: 10px">
        <el-button
          type="primary"
          @click="handleSaveQueries"
          :loading="savingQueries"
          :disabled="Object.keys(editingQueries).length === 0"
        >
          保存修改 ({{ Object.keys(editingQueries).length }})
        </el-button>
        <el-button
          type="danger"
          @click="handleDeleteQueries"
          :loading="deletingQueries"
          :disabled="selectedQueryIds.length === 0"
        >
          删除选中 ({{ selectedQueryIds.length }})
        </el-button>
      </div>

      <el-table
        v-loading="loadingQueries"
        :data="queries"
        border
        stripe
        style="width: 100%"
        :empty-text="selectedNamespace ? '该命名空间下暂无查询' : '请先选择命名空间'"
        @selection-change="handleQuerySelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="statementId" label="语句ID" width="200">
          <template #default="{ row }">
            <el-input
              v-if="editingQueries[row.id]"
              v-model="editingQueries[row.id].statementId"
              size="small"
            />
            <span v-else>{{ row.statementId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="queryType" label="查询类型" width="100">
          <template #default="{ row }">
            <el-input
              v-if="editingQueries[row.id]"
              v-model="editingQueries[row.id].queryType"
              size="small"
            />
            <span v-else>{{ row.queryType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tableName" label="表名" width="200">
          <template #default="{ row }">
            <el-input
              v-if="editingQueries[row.id]"
              v-model="editingQueries[row.id].tableName"
              size="small"
            />
            <span v-else>{{ row.tableName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sql" label="SQL语句" min-width="300">
          <template #default="{ row }">
            <el-input
              v-if="editingQueries[row.id]"
              v-model="editingQueries[row.id].sql"
              type="textarea"
              :rows="2"
              size="small"
            />
            <span v-else style="white-space: pre-wrap">{{ row.sql }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dynamicConditions" label="动态条件" width="200">
          <template #default="{ row }">
            <el-input
              v-if="editingQueries[row.id]"
              v-model="editingQueries[row.id].dynamicConditions"
              type="textarea"
              :rows="2"
              size="small"
            />
            <span v-else>{{ row.dynamicConditions || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="!editingQueries[row.id]"
              type="primary"
              link
              size="small"
              @click="startEditQuery(row)"
            >
              编辑
            </el-button>
            <el-button
              v-else
              type="success"
              link
              size="small"
              @click="cancelEditQuery(row.id)"
            >
              取消
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 第三部分：参数列表 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <span>Mapper参数列表</span>
        </div>
      </template>

      <el-alert
        v-if="selectedNamespace"
        :title="`当前命名空间: ${selectedNamespace}`"
        type="info"
        :closable="false"
        style="margin-bottom: 20px"
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
        :empty-text="selectedNamespace ? '该命名空间下暂无参数' : '请先选择命名空间'"
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  uploadMapperXml,
  getNamespaces,
  getQueriesByNamespace,
  getParametersByNamespace,
  updateQuery,
  deleteQueries,
  updateParameter,
  deleteParameters,
  parseByNamespace
} from '@/api'

const uploading = ref(false)
const error = ref(null)
const success = ref(null)

const form = reactive({
  mapperNamespace: '',
  xmlContent: ''
})

// Namespace 相关
const namespaces = ref([])
const selectedNamespace = ref('')
const loadingNamespaces = ref(false)

// 查询列表相关
const queries = ref([])
const loadingQueries = ref(false)
const editingQueries = ref({}) // 存储正在编辑的查询数据
const selectedQueryIds = ref([]) // 选中的查询ID列表
const savingQueries = ref(false)
const deletingQueries = ref(false)

// 参数列表相关
const parameters = ref([])
const loadingParameters = ref(false)
const editingParameters = ref({}) // 存储正在编辑的参数数据
const selectedParameterIds = ref([]) // 选中的参数ID列表
const savingParameters = ref(false)
const deletingParameters = ref(false)

// 加载命名空间列表
async function loadNamespaces() {
  loadingNamespaces.value = true
  try {
    namespaces.value = await getNamespaces()
  } catch (err) {
    console.error('加载命名空间列表失败:', err)
    error.value = '加载命名空间列表失败: ' + (err.message || '未知错误')
  } finally {
    loadingNamespaces.value = false
  }
}

// 处理命名空间变化
async function handleNamespaceChange() {
  if (!selectedNamespace.value) {
    queries.value = []
    parameters.value = []
    // 清空编辑和选中状态
    editingQueries.value = {}
    editingParameters.value = {}
    selectedQueryIds.value = []
    selectedParameterIds.value = []
    return
  }

  // 加载查询列表
  loadingQueries.value = true
  try {
    queries.value = await getQueriesByNamespace(selectedNamespace.value)
    // 清空编辑和选中状态
    editingQueries.value = {}
    selectedQueryIds.value = []
  } catch (err) {
    console.error('加载查询列表失败:', err)
    error.value = '加载查询列表失败: ' + (err.message || '未知错误')
  } finally {
    loadingQueries.value = false
  }

  // 加载参数列表
  loadingParameters.value = true
  try {
    parameters.value = await getParametersByNamespace(selectedNamespace.value)
    // 清空编辑和选中状态
    editingParameters.value = {}
    selectedParameterIds.value = []
  } catch (err) {
    console.error('加载参数列表失败:', err)
    error.value = '加载参数列表失败: ' + (err.message || '未知错误')
  } finally {
    loadingParameters.value = false
  }
}

// 上传处理
async function handleUpload() {
  const namespace = form.mapperNamespace.trim()
  const xmlContent = form.xmlContent.trim()

  // 至少需要填写一个
  if (!namespace && !xmlContent) {
    error.value = '请至少填写Mapper命名空间或XML内容'
    return
  }

  // 如果只有XML内容，需要namespace
  if (!namespace && xmlContent) {
    error.value = '解析XML内容需要提供Mapper命名空间'
    return
  }

  uploading.value = true
  error.value = null
  success.value = null

  try {
    let data

    if (xmlContent) {
      // 如果提供了XML内容，使用XML上传解析
      data = await uploadMapperXml({
        mapperNamespace: namespace,
        xmlContent: xmlContent
      })
      success.value = `成功解析 ${data.queryCount} 个SQL查询！`
      form.xmlContent = ''
    } else {
      // 如果只提供了namespace，使用namespace解析（从应用上下文获取Configuration）
      data = await parseByNamespace(namespace)
      
      if (data.needEdit === true) {
        success.value = `已提取 ${data.parameters?.length || 0} 个参数，请编辑参数后继续解析SQL`
      } else {
        success.value = `成功解析 ${data.queryCount || 0} 个SQL查询！`
      }
    }
    
    // 解析成功后刷新命名空间列表和查询列表
    await loadNamespaces()
    // 如果解析的命名空间已选中，刷新查询和参数列表
    if (selectedNamespace.value === namespace) {
      await handleNamespaceChange()
    }
  } catch (err) {
    error.value = err.message || '解析失败，请检查网络连接或稍后重试'
    console.error('解析错误:', err)
  } finally {
    uploading.value = false
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

// 查询相关操作
function startEditQuery(row) {
  editingQueries.value[row.id] = {
    mapperNamespace: row.mapperNamespace,
    statementId: row.statementId,
    queryType: row.queryType,
    sql: row.sql,
    tableName: row.tableName,
    dynamicConditions: row.dynamicConditions || ''
  }
}

function cancelEditQuery(id) {
  delete editingQueries.value[id]
}

function handleQuerySelectionChange(selection) {
  selectedQueryIds.value = selection.map(item => item.id)
}

async function handleSaveQueries() {
  const editingIds = Object.keys(editingQueries.value)
  if (editingIds.length === 0) {
    ElMessage.warning('没有需要保存的修改')
    return
  }

  savingQueries.value = true
  try {
    const promises = editingIds.map(id => {
      const editData = editingQueries.value[id]
      return updateQuery(parseInt(id), editData)
    })

    await Promise.all(promises)
    ElMessage.success(`成功保存 ${editingIds.length} 个查询`)
    
    // 清空编辑状态
    editingQueries.value = {}
    
    // 刷新列表
    if (selectedNamespace.value) {
      await handleNamespaceChange()
    }
  } catch (err) {
    ElMessage.error('保存失败: ' + (err.message || '未知错误'))
    console.error('保存查询失败:', err)
  } finally {
    savingQueries.value = false
  }
}

async function handleDeleteQueries() {
  if (selectedQueryIds.value.length === 0) {
    ElMessage.warning('请选择要删除的查询')
    return
  }

  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedQueryIds.value.length} 个查询吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    deletingQueries.value = true
    try {
      await deleteQueries(selectedQueryIds.value)
      ElMessage.success(`成功删除 ${selectedQueryIds.value.length} 个查询`)
      
      // 清空选中和编辑状态
      selectedQueryIds.value = []
      editingQueries.value = {}
      
      // 刷新列表
      if (selectedNamespace.value) {
        await handleNamespaceChange()
      }
    } catch (err) {
      ElMessage.error('删除失败: ' + (err.message || '未知错误'))
      console.error('删除查询失败:', err)
    } finally {
      deletingQueries.value = false
    }
  } catch {
    // 用户取消删除
  }
}

// 参数相关操作
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
    const promises = editingIds.map(id => {
      const editData = editingParameters.value[id]
      return updateParameter(parseInt(id), editData)
    })

    await Promise.all(promises)
    ElMessage.success(`成功保存 ${editingIds.length} 个参数`)
    
    // 清空编辑状态
    editingParameters.value = {}
    
    // 刷新列表
    if (selectedNamespace.value) {
      await handleNamespaceChange()
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
      if (selectedNamespace.value) {
        await handleNamespaceChange()
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

// 组件挂载时加载命名空间列表
onMounted(() => {
  loadNamespaces()
})
</script>

<style scoped>
.mapper-upload-container {
  padding: 20px;
}

.card-header {
  font-weight: 600;
  font-size: 18px;
}
</style>

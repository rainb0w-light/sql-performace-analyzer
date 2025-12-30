<template>
  <div>
    <el-card style="margin-bottom: 20px">
      <template #header>
        <div class="card-header">
          <span>{{ editingParameter ? '编辑参数' : '添加新参数' }}</span>
        </div>
      </template>

      <el-form :model="parameterForm" label-width="120px">
        <el-form-item label="Mapper ID：">
          <el-input
            v-model="parameterForm.mapperId"
            placeholder="例如: com.example.mapper.UserMapper.selectById 或 com.example.mapper.UserMapper"
            :disabled="savingParameter"
          />
          <div style="font-size: 12px; color: #666; margin-top: 5px">
            支持完整路径（如：com.example.demo.selectByKey）或命名空间层级（如：com.example.demo）
          </div>
        </el-form-item>

        <el-form-item label="参数JSON：">
          <el-input
            v-model="parameterForm.parameterJson"
            type="textarea"
            :rows="6"
            placeholder='例如: {"id": 1, "username": "test", "status": "active"}'
            :disabled="savingParameter"
          />
          <div style="font-size: 12px; color: #666; margin-top: 5px">
            请输入有效的JSON格式，将被解析为Map&lt;String,Object&gt;
          </div>
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            @click="handleSave"
            :loading="savingParameter"
            :disabled="!parameterForm.mapperId.trim() || !parameterForm.parameterJson.trim()"
          >
            {{ savingParameter ? '保存中...' : (editingParameter ? '更新' : '保存') }}
          </el-button>
          <el-button
            v-if="editingParameter"
            @click="handleCancel"
            :disabled="savingParameter"
          >
            取消
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

    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>参数列表（共 {{ parameters.length }} 条）</span>
          <el-button @click="loadParameters" :loading="loadingParameters" size="small">
            刷新
          </el-button>
        </div>
      </template>

      <el-empty v-if="!loadingParameters && parameters.length === 0" description="暂无参数，请添加新参数" />

      <el-table v-else :data="parameters" border>
        <el-table-column prop="mapperId" label="Mapper ID" />
        <el-table-column prop="parameterJson" label="参数JSON" show-overflow-tooltip />
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.updatedAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row.mapperId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { getParameters, saveParameter, deleteParameter } from '@/api'

const loadingParameters = ref(false)
const savingParameter = ref(false)
const error = ref(null)
const success = ref(null)
const parameters = ref([])
const editingParameter = ref(null)

const parameterForm = reactive({
  mapperId: '',
  parameterJson: ''
})

onMounted(() => {
  loadParameters()
})

async function loadParameters() {
  loadingParameters.value = true
  error.value = null

  try {
    parameters.value = await getParameters()
  } catch (err) {
    error.value = err.message || '加载参数列表失败'
    console.error('加载参数错误:', err)
  } finally {
    loadingParameters.value = false
  }
}

async function handleSave() {
  if (!parameterForm.mapperId.trim() || !parameterForm.parameterJson.trim()) {
    error.value = '请填写Mapper ID和参数JSON'
    return
  }

  try {
    JSON.parse(parameterForm.parameterJson)
  } catch (e) {
    error.value = '参数JSON格式无效，请检查JSON语法'
    return
  }

  savingParameter.value = true
  error.value = null
  success.value = null

  try {
    await saveParameter({
      mapperId: parameterForm.mapperId.trim(),
      parameters: JSON.parse(parameterForm.parameterJson)
    })

    success.value = '参数保存成功！'
    resetForm()
    await loadParameters()
  } catch (err) {
    error.value = err.message || '保存失败，请检查网络连接或稍后重试'
    console.error('保存参数错误:', err)
  } finally {
    savingParameter.value = false
  }
}

function handleEdit(param) {
  editingParameter.value = param
  parameterForm.mapperId = param.mapperId
  parameterForm.parameterJson = param.parameterJson
}

function handleCancel() {
  resetForm()
}

function resetForm() {
  parameterForm.mapperId = ''
  parameterForm.parameterJson = ''
  editingParameter.value = null
  error.value = null
  success.value = null
}

async function handleDelete(mapperId) {
  try {
    await ElMessageBox.confirm(
      `确定要删除参数 "${mapperId}" 吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    await deleteParameter(mapperId)
    success.value = '参数删除成功！'
    await loadParameters()
  } catch (err) {
    if (err !== 'cancel') {
      error.value = err.message || '删除失败'
      console.error('删除参数错误:', err)
    }
  }
}

function formatDateTime(dateTimeStr) {
  if (!dateTimeStr) return '-'
  try {
    const date = new Date(dateTimeStr)
    return date.toLocaleString('zh-CN')
  } catch (e) {
    return dateTimeStr
  }
}
</script>

<style scoped>
.card-header {
  font-weight: 600;
  font-size: 18px;
}
</style>



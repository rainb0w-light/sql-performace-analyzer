<template>
  <div>
    <el-card style="margin-bottom: 20px">
      <template #header>
        <div class="card-header">
          <span>执行 ANALYZE TABLE</span>
        </div>
      </template>

      <el-form :model="form" label-width="100px" inline>
        <el-form-item label="表名：">
          <el-input
            v-model="form.tableName"
            placeholder="例如: users"
            :disabled="analyzing"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="数据源：">
          <el-select
            v-model="form.datasource"
            placeholder="请选择数据源"
            :disabled="analyzing || datasources.length === 0"
            style="width: 300px"
          >
            <el-option
              v-for="ds in datasources"
              :key="ds.name"
              :label="`${ds.name} (${ds.url})`"
              :value="ds.name"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            @click="handleAnalyze"
            :loading="analyzing"
            :disabled="!form.tableName.trim()"
          >
            {{ analyzing ? '执行中...' : '执行 ANALYZE TABLE' }}
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
        v-if="successMessage"
        :title="successMessage"
        type="success"
        :closable="false"
        show-icon
        style="margin-top: 20px"
      />
    </el-card>

    <!-- ANALYZE TABLE 详细结果 -->
    <el-card v-if="analyzeTableResult" style="margin-top: 20px">
      <template #header>
        <div class="card-header">
          <span>ANALYZE TABLE 执行详情</span>
        </div>
      </template>

      <div v-if="analyzeTableResult.messages && analyzeTableResult.messages.length > 0">
        <h4 style="margin-bottom: 15px">执行消息：</h4>
        <el-scrollbar height="400px">
          <div v-for="(msg, index) in analyzeTableResult.messages" :key="index" class="message-item">
            <el-tag
              :type="getMessageType(msg)"
              size="small"
              style="margin-right: 10px; min-width: 60px"
            >
              {{ getMessageTypeLabel(msg) }}
            </el-tag>
            <span>{{ msg }}</span>
          </div>
        </el-scrollbar>
      </div>

      <el-alert
        v-if="analyzeTableResult.errorMessage"
        :title="analyzeTableResult.errorMessage"
        type="warning"
        :closable="false"
        show-icon
        style="margin-top: 15px"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getDatasources, analyzeTable } from '@/api'

const analyzing = ref(false)
const error = ref(null)
const successMessage = ref(null)
const analyzeTableResult = ref(null)
const datasources = ref([])

const form = reactive({
  tableName: '',
  datasource: ''
})

onMounted(async () => {
  await loadDatasources()
})

async function loadDatasources() {
  try {
    datasources.value = await getDatasources()
    if (datasources.value.length === 1) {
      form.datasource = datasources.value[0].name
    }
  } catch (err) {
    console.error('加载数据源列表错误:', err)
  }
}

async function handleAnalyze() {
  if (!form.tableName.trim()) {
    error.value = '请输入表名'
    return
  }

  analyzing.value = true
  error.value = null
  successMessage.value = null
  analyzeTableResult.value = null

  try {
    const data = await analyzeTable(form.tableName.trim(), form.datasource)
    if (!data.success) {
      throw new Error(data.message || '执行失败')
    }
    successMessage.value = data.message || 'ANALYZE TABLE 执行成功'
    
    // 保存详细结果
    if (data.analyzeTableResult) {
      analyzeTableResult.value = data.analyzeTableResult
    }
  } catch (err) {
    error.value = err.message || '执行失败，请检查网络连接或稍后重试'
    console.error('执行错误:', err)
  } finally {
    analyzing.value = false
  }
}

function getMessageType(msg) {
  if (!msg) return 'info'
  const lowerMsg = msg.toLowerCase()
  if (lowerMsg.includes('error') || lowerMsg.includes('失败')) {
    return 'danger'
  }
  if (lowerMsg.includes('warning') || lowerMsg.includes('警告')) {
    return 'warning'
  }
  if (lowerMsg.includes('created') || lowerMsg.includes('成功') || lowerMsg.includes('完成')) {
    return 'success'
  }
  return 'info'
}

function getMessageTypeLabel(msg) {
  if (!msg) return '信息'
  const lowerMsg = msg.toLowerCase()
  if (lowerMsg.includes('error') || lowerMsg.includes('失败')) {
    return '错误'
  }
  if (lowerMsg.includes('warning') || lowerMsg.includes('警告')) {
    return '警告'
  }
  if (lowerMsg.includes('created') || lowerMsg.includes('成功') || lowerMsg.includes('完成')) {
    return '成功'
  }
  return '信息'
}
</script>

<style scoped>
.card-header {
  font-weight: 600;
  font-size: 18px;
}

.message-item {
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: flex-start;
  line-height: 1.6;
}

.message-item:last-child {
  border-bottom: none;
}
</style>



<template>
  <div>
    <el-card style="margin-bottom: 20px">
      <template #header>
        <div class="card-header">
          <span>分析指定表的所有查询</span>
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
            {{ analyzing ? '分析中...' : '开始分析' }}
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
    </el-card>

    <AnalysisResultCard v-if="analysisResult" :result="analysisResult" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getDatasources, analyzeTable } from '@/api'
import AnalysisResultCard from './AnalysisResultCard.vue'

const analyzing = ref(false)
const error = ref(null)
const analysisResult = ref(null)
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
  analysisResult.value = null

  try {
    const data = await analyzeTable(form.tableName.trim(), form.datasource)
    if (!data.success) {
      throw new Error(data.message || '分析失败')
    }
    analysisResult.value = data
  } catch (err) {
    error.value = err.message || '分析失败，请检查网络连接或稍后重试'
    console.error('分析错误:', err)
  } finally {
    analyzing.value = false
  }
}
</script>

<style scoped>
.card-header {
  font-weight: 600;
  font-size: 18px;
}
</style>



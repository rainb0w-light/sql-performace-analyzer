<template>
  <div>
    <el-card style="margin-bottom: 20px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="SQL 语句">
          <el-input
            v-model="form.sql"
            type="textarea"
            :rows="8"
            placeholder="输入 SQL 语句，例如：SELECT * FROM users WHERE age > ? AND city = ?"
          />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="数据源">
              <el-select v-model="form.datasource" placeholder="选择数据源" style="width: 100%">
                <el-option
                  v-for="ds in datasources"
                  :key="ds.name"
                  :label="ds.name"
                  :value="ds.name"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="LLM 模型">
              <el-select v-model="form.llm" placeholder="选择 LLM 模型" style="width: 100%">
                <el-option
                  v-for="llm in llms"
                  :key="llm.name"
                  :label="`${llm.name} (${llm.model})`"
                  :value="llm.name"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item>
          <el-button
            type="primary"
            @click="handleAnalyze"
            :loading="loading"
            :disabled="!form.sql || !form.datasource || !form.llm"
          >
            {{ loading ? '分析中...' : '开始分析' }}
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

    <ResultViewer v-if="result" :result="result" :is-mapper="false" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getDatasources, getLlms, analyzeAgent } from '@/api'
import ResultViewer from './ResultViewer.vue'

const loading = ref(false)
const errorMessage = ref('')
const result = ref(null)
const datasources = ref([])
const llms = ref([])

const form = reactive({
  sql: '',
  datasource: '',
  llm: ''
})

onMounted(async () => {
  await loadDatasources()
  await loadLlms()
})

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

async function handleAnalyze() {
  loading.value = true
  errorMessage.value = ''
  result.value = null

  try {
    const data = await analyzeAgent({
      sql: form.sql,
      datasourceName: form.datasource,
      llmName: form.llm
    })
    result.value = data
  } catch (error) {
    errorMessage.value = '分析失败: ' + error.message
  } finally {
    loading.value = false
  }
}

function handleClear() {
  form.sql = ''
  form.datasource = ''
  form.llm = ''
  result.value = null
  errorMessage.value = ''
}
</script>



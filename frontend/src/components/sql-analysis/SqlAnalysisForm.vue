<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>SQL 性能分析</span>
      </div>
    </template>

    <el-form :model="form" label-width="100px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="数据源：">
            <el-select
              v-model="form.datasource"
              placeholder="请选择数据源"
              :disabled="loading || datasources.length === 0"
              style="width: 100%"
            >
              <el-option
                v-for="ds in datasources"
                :key="ds.name"
                :label="`${ds.name} (${ds.url})`"
                :value="ds.name"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="大模型：">
            <el-select
              v-model="form.llm"
              placeholder="请选择大模型"
              :disabled="loading || llms.length === 0"
              style="width: 100%"
            >
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

      <el-form-item label="SQL 语句：">
        <el-input
          v-model="form.sql"
          type="textarea"
          :rows="8"
          placeholder="例如: SELECT * FROM users WHERE id = 1"
          :disabled="loading"
        />
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          @click="handleAnalyze"
          :loading="loading"
          :disabled="!form.sql.trim()"
        >
          开始分析
        </el-button>
        <el-button
          @click="handleExportPdf"
          :disabled="!props.report"
        >
          导出 PDF
        </el-button>
        <el-button
          @click="handleDownloadMarkdown"
          :loading="downloadingMarkdown"
          :disabled="!props.report"
        >
          {{ downloadingMarkdown ? '下载中...' : '下载 Markdown' }}
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
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getDatasources, getLlms, analyzeSql, downloadSqlReport } from '@/api'
import { exportToPdf } from '@/utils/pdf'

const props = defineProps({
  report: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['analyze', 'export-pdf', 'download-markdown'])

const loading = ref(false)
const error = ref(null)
const datasources = ref([])
const llms = ref([])
const downloadingMarkdown = ref(false)

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
    if (datasources.value.length === 1) {
      form.datasource = datasources.value[0].name
    }
  } catch (err) {
    console.error('加载数据源列表错误:', err)
  }
}

async function loadLlms() {
  try {
    llms.value = await getLlms()
    if (llms.value.length === 1) {
      form.llm = llms.value[0].name
    }
  } catch (err) {
    console.error('加载大模型列表错误:', err)
  }
}

async function handleAnalyze() {
  if (!form.sql.trim()) {
    error.value = '请输入 SQL 语句'
    return
  }

  loading.value = true
  error.value = null

  try {
    const requestBody = {
      sql: form.sql.trim()
    }
    if (form.datasource) {
      requestBody.datasourceName = form.datasource
    }
    if (form.llm) {
      requestBody.llmName = form.llm
    }

    const data = await analyzeSql(requestBody)
    emit('analyze', data)
  } catch (err) {
    error.value = err.message || '分析失败，请检查网络连接或稍后重试'
    console.error('分析错误:', err)
  } finally {
    loading.value = false
  }
}

async function handleExportPdf() {
  if (!props.report) {
    error.value = '没有可导出的报告'
    return
  }

  try {
    const element = document.getElementById('report-content')
    if (element) {
      await exportToPdf(element)
    }
  } catch (err) {
    error.value = err.message
    console.error('PDF 导出错误:', err)
  }
}

async function handleDownloadMarkdown() {
  if (!props.report) {
    error.value = '没有可下载的报告'
    return
  }

  downloadingMarkdown.value = true
  error.value = null

  try {
    const requestBody = {
      sql: form.sql.trim()
    }
    if (form.datasource) {
      requestBody.datasourceName = form.datasource
    }
    if (form.llm) {
      requestBody.llmName = form.llm
    }

    const response = await downloadSqlReport(requestBody)

    const contentDisposition = response.headers.get('Content-Disposition')
    let filename = 'sql-analysis-report.md'
    if (contentDisposition) {
      const filenameMatch = contentDisposition.match(/filename="?(.+)"?/)
      if (filenameMatch) {
        filename = filenameMatch[1]
      }
    }

    const blob = await response.blob()
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    window.URL.revokeObjectURL(url)
    document.body.removeChild(a)
  } catch (err) {
    error.value = 'Markdown 下载失败: ' + err.message
    console.error('Markdown 下载错误:', err)
  } finally {
    downloadingMarkdown.value = false
  }
}
</script>

<style scoped>
.card-header {
  font-weight: 600;
  font-size: 18px;
}
</style>


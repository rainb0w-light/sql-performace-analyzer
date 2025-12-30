<template>
  <div class="sql-analysis">
    <SqlAnalysisForm
      :report="report"
      @analyze="handleAnalyze"
      @export-pdf="handleExportPdf"
      @download-markdown="handleDownloadMarkdown"
    />
    <SqlAnalysisResult :report="report" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import SqlAnalysisForm from '@/components/sql-analysis/SqlAnalysisForm.vue'
import SqlAnalysisResult from '@/components/sql-analysis/SqlAnalysisResult.vue'
import { exportToPdf } from '@/utils/pdf'

const report = ref('')

function handleAnalyze(data) {
  if (data.report) {
    report.value = data.report
  }
}

async function handleExportPdf() {
  if (!report.value) return
  try {
    const element = document.getElementById('report-content')
    if (element) {
      await exportToPdf(element)
    }
  } catch (err) {
    console.error('PDF 导出错误:', err)
  }
}

function handleDownloadMarkdown() {
  // 这个功能在 SqlAnalysisForm 中已经实现
}
</script>

<style scoped>
.sql-analysis {
  width: 100%;
}
</style>


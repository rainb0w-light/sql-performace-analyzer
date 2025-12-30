<template>
  <div>
    <!-- 单条 SQL 分析结果 -->
    <div v-if="!isMapper && result">
      <el-card>
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <div>
              <span>分析结果</span>
              <el-tag
                :type="getRiskType(result.finalRiskLevel)"
                style="margin-left: 10px"
              >
                {{ result.finalRiskLevel || 'N/A' }}
              </el-tag>
              <span v-if="result.processingTimeMs" style="margin-left: 10px; font-size: 12px; color: #666">
                ⏱️ {{ (result.processingTimeMs / 1000).toFixed(2) }}s
              </span>
            </div>
            <el-button @click="downloadMarkdown(result, false)" type="primary" size="small">
              📥 下载 Markdown 报告
            </el-button>
          </div>
        </template>

        <SingleResultContent :result="result" />
      </el-card>
    </div>

    <!-- Mapper 批量分析结果 -->
    <div v-if="isMapper && result">
      <el-card>
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center">
            <span>批量分析结果</span>
            <el-button @click="downloadMarkdown(result, true)" type="primary" size="small">
              📥 下载 Markdown 报告
            </el-button>
          </div>
        </template>

        <el-alert
          :title="`Namespace: ${result.mapperNamespace}`"
          type="info"
          :closable="false"
          style="margin-bottom: 20px"
        >
          <template #default>
            <div>
              <strong>Namespace:</strong> {{ result.mapperNamespace }}<br>
              <strong>总结:</strong> {{ result.overallSummary || '已完成批量分析' }}
            </div>
          </template>
        </el-alert>

        <div v-for="(item, index) in result.results" :key="index" style="margin-bottom: 20px">
          <el-card shadow="hover">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <span>SQL #{{ index + 1 }}</span>
                <div>
                  <el-tag
                    :type="getRiskType(item.finalRiskLevel)"
                    style="margin-right: 10px"
                  >
                    {{ item.finalRiskLevel || 'N/A' }}
                  </el-tag>
                  <span v-if="item.processingTimeMs" style="font-size: 12px; color: #666">
                    ⏱️ {{ (item.processingTimeMs / 1000).toFixed(2) }}s
                  </span>
                </div>
              </div>
            </template>

            <SingleResultContent :result="item" />
          </el-card>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { defineProps } from 'vue'
import SingleResultContent from './SingleResultContent.vue'
import { generateSingleMarkdownReport, generateMapperMarkdownReport } from '@/utils/markdown-reports'

const props = defineProps({
  result: {
    type: Object,
    required: true
  },
  isMapper: {
    type: Boolean,
    default: false
  }
})

function getRiskType(level) {
  const levelMap = {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger',
    CRITICAL: 'danger'
  }
  return levelMap[level] || 'info'
}

function downloadMarkdown(data, isMapper) {
  let markdown = ''
  
  if (isMapper) {
    markdown = generateMapperMarkdownReport(data)
  } else {
    markdown = generateSingleMarkdownReport(data)
  }
  
  const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').split('T')[0]
  link.download = isMapper ? 
    `SQL批量分析报告_${data.mapperNamespace}_${timestamp}.md` : 
    `SQL分析报告_${timestamp}.md`
  
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
</script>



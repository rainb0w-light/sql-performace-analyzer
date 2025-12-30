<template>
  <div>
    <!-- 分析统计 -->
    <el-card style="margin-bottom: 20px">
      <template #header>
        <span>分析统计</span>
      </template>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-statistic title="总查询数" :value="result.queryCount" />
        </el-col>
        <el-col :span="8">
          <el-statistic
            title="慢查询数"
            :value="result.suggestions?.slowQueries || 0"
            :value-style="{ color: '#f44336' }"
          />
        </el-col>
        <el-col :span="8">
          <el-statistic
            title="未使用索引"
            :value="result.suggestions?.queriesWithoutIndex || 0"
            :value-style="{ color: '#ff9800' }"
          />
        </el-col>
      </el-row>
    </el-card>

    <!-- 表结构 -->
    <el-card v-if="result.tableStructure" style="margin-bottom: 20px">
      <template #header>
        <span>表结构信息</span>
      </template>

      <h4 style="margin-bottom: 15px">列信息</h4>
      <el-table :data="result.tableStructure.columns" border style="margin-bottom: 20px">
        <el-table-column prop="columnName" label="列名" />
        <el-table-column prop="dataType" label="数据类型" />
        <el-table-column prop="isNullable" label="可空" />
        <el-table-column prop="columnKey" label="键" />
        <el-table-column prop="columnDefault" label="默认值" />
        <el-table-column prop="extra" label="额外" />
      </el-table>

      <h4 style="margin-bottom: 15px">索引信息</h4>
      <el-table :data="result.tableStructure.indexes" border>
        <el-table-column prop="indexName" label="索引名" />
        <el-table-column prop="columnName" label="列名" />
        <el-table-column prop="nonUnique" label="唯一性">
          <template #default="{ row }">
            {{ row.nonUnique === 0 ? '唯一' : '非唯一' }}
          </template>
        </el-table-column>
        <el-table-column prop="seqInIndex" label="顺序" />
        <el-table-column prop="indexType" label="索引类型" />
      </el-table>
    </el-card>

    <!-- 优化建议 -->
    <el-card v-if="result.suggestions" style="margin-bottom: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>优化建议</span>
          <el-button
            v-if="optimizationMarkdown"
            @click="handleDownloadMarkdown"
            type="primary"
            size="small"
          >
            下载 Markdown
          </el-button>
        </div>
      </template>

      <div v-if="result.suggestions.indexSuggestions && result.suggestions.indexSuggestions.length > 0">
        <h4 style="margin-bottom: 10px; color: #666">索引建议</h4>
        <ul>
          <li v-for="(suggestion, index) in result.suggestions.indexSuggestions" :key="index" style="margin-bottom: 8px">
            {{ suggestion }}
          </li>
        </ul>
      </div>

      <div v-if="optimizationMarkdown" style="margin-top: 20px">
        <h4 style="margin-bottom: 10px; color: #2196F3">🤖 AI智能优化建议</h4>
        <div class="markdown-content" v-html="renderMarkdown(optimizationMarkdown)" />
      </div>

      <div v-if="(!result.suggestions.indexSuggestions || result.suggestions.indexSuggestions.length === 0) && !optimizationMarkdown">
        <p style="color: #666; margin-top: 15px">暂无优化建议，所有查询性能良好。</p>
      </div>
    </el-card>

    <!-- 查询分析列表 -->
    <el-card v-if="result.queryAnalyses && result.queryAnalyses.length > 0">
      <template #header>
        <span>查询分析详情</span>
      </template>

      <div v-for="query in result.queryAnalyses" :key="query.queryId" class="query-item" :class="{ 'slow-query': query.slowQuery }">
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px">
          <h4>
            {{ query.statementId || '查询 #' + query.queryId }}
            <span v-if="query.mapperNamespace" style="color: #666; font-size: 12px; font-weight: normal">
              ({{ query.mapperNamespace }})
            </span>
          </h4>
          <div>
            <el-tag :type="query.slowQuery ? 'danger' : 'success'">
              {{ query.slowQuery ? '慢查询' : '正常' }}
            </el-tag>
            <el-tag v-if="query.queryType" type="warning" style="margin-left: 5px">
              {{ query.queryType.toUpperCase() }}
            </el-tag>
          </div>
        </div>

        <el-card shadow="never" style="margin-bottom: 10px">
          <div style="margin-bottom: 5px">
            <strong style="color: #666; font-size: 12px">原始SQL:</strong>
          </div>
          <pre class="sql-code">{{ query.sql }}</pre>
        </el-card>

        <el-card
          v-if="query.executableSql && query.executableSql !== query.sql"
          shadow="never"
          style="margin-bottom: 10px; background: #e8f5e9; border-left: 3px solid #4CAF50"
        >
          <div style="margin-bottom: 5px">
            <strong style="color: #2e7d32; font-size: 12px">可执行SQL（已替换参数）:</strong>
          </div>
          <pre class="sql-code">{{ query.executableSql }}</pre>
        </el-card>

        <div v-if="query.dynamicConditions" style="margin-bottom: 8px; font-size: 12px; color: #666">
          动态条件: {{ query.dynamicConditions }}
        </div>

        <el-alert
          v-if="query.error"
          :title="query.error"
          type="error"
          :closable="false"
          show-icon
          style="margin-top: 10px"
        />

        <div v-else class="query-info">
          <el-descriptions :column="4" border size="small">
            <el-descriptions-item label="使用索引">
              {{ query.usesIndex ? '是' : '否' }}
            </el-descriptions-item>
            <el-descriptions-item v-if="query.indexName" label="索引名">
              {{ query.indexName }}
            </el-descriptions-item>
            <el-descriptions-item v-if="query.accessType" label="访问类型">
              {{ query.accessType }}
            </el-descriptions-item>
            <el-descriptions-item
              v-if="query.rowsExamined !== null && query.rowsExamined !== undefined"
              label="扫描行数"
            >
              {{ query.rowsExamined }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { parseMarkdown } from '@/utils/markdown'

const props = defineProps({
  result: {
    type: Object,
    required: true
  }
})

const optimizationMarkdown = computed(() => {
  if (!props.result.suggestions) return null
  const suggestions = props.result.suggestions
  if (suggestions.aiSuggestions && suggestions.aiSuggestions.trim()) {
    return suggestions.aiSuggestions
  }
  if (suggestions.sqlSuggestions && suggestions.sqlSuggestions.length > 0) {
    return suggestions.sqlSuggestions.join('\n\n')
  }
  return null
})

function renderMarkdown(markdown) {
  return parseMarkdown(markdown)
}

function handleDownloadMarkdown() {
  const markdown = optimizationMarkdown.value
  if (!markdown) {
    return
  }

  const tableName = props.result?.tableName || '未知表'
  const datasourceName = props.result?.datasourceName || ''
  const queryCount = props.result?.queryCount || 0
  const timestamp = new Date().toLocaleString('zh-CN')

  let fullMarkdown = `# ${tableName} 表优化建议\n\n`
  fullMarkdown += `**生成时间**: ${timestamp}\n\n`
  if (datasourceName) {
    fullMarkdown += `**数据源**: ${datasourceName}\n\n`
  }
  fullMarkdown += `**查询数量**: ${queryCount}\n\n`
  fullMarkdown += `---\n\n`

  if (props.result?.suggestions?.indexSuggestions && props.result.suggestions.indexSuggestions.length > 0) {
    fullMarkdown += `## 索引建议\n\n`
    props.result.suggestions.indexSuggestions.forEach(suggestion => {
      fullMarkdown += `- ${suggestion}\n`
    })
    fullMarkdown += `\n`
  }

  fullMarkdown += `## AI智能优化建议\n\n`
  fullMarkdown += markdown

  const blob = new Blob([fullMarkdown], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${tableName}_优化建议_${new Date().toISOString().split('T')[0]}.md`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.query-item {
  padding: 15px;
  margin-bottom: 15px;
  background: white;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
}

.query-item.slow-query {
  border-left: 4px solid #f44336;
}

.sql-code {
  background: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  overflow-x: auto;
  margin: 0;
}

.query-info {
  margin-top: 10px;
}

.markdown-content {
  line-height: 1.8;
}
</style>

<style>
@import '@/styles/main.css';
</style>



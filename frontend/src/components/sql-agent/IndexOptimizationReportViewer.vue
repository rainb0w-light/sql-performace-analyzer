<template>
  <div class="index-optimization-report">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>索引优化报告</span>
          <el-button @click="downloadReport" type="primary" size="small">
            📥 下载报告
          </el-button>
        </div>
      </template>

      <!-- Markdown 报告内容 -->
      <div v-if="reportContent" class="markdown-content">
        <div v-html="renderMarkdown(reportContent)"></div>
      </div>
      <div v-else class="empty-content">
        <p>暂无报告内容</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
const props = defineProps({
  reportContent: {
    type: String,
    default: ''
  }
})

function renderMarkdown(markdown) {
  if (!markdown) return ''
  
  // 先提取代码块，避免被后续处理影响
  const codeBlocks = []
  let processedMarkdown = markdown.replace(/```(\w+)?\n?([\s\S]*?)```/g, (match, lang, code) => {
    const placeholder = `__CODE_BLOCK_${codeBlocks.length}__`
    codeBlocks.push({ placeholder, code: code.trim(), lang: lang || '' })
    return placeholder
  })
  
  // 转义HTML（代码块已提取，不会被转义）
  let html = processedMarkdown
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  
  // 标题
  html = html.replace(/^### (.*$)/gim, '<h3>$1</h3>')
  html = html.replace(/^## (.*$)/gim, '<h2>$1</h2>')
  html = html.replace(/^# (.*$)/gim, '<h1>$1</h1>')
  
  // 粗体
  html = html.replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
  
  // 行内代码（需要转义）
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  
  // 列表
  html = html.replace(/^\s*[-*+]\s+(.*)$/gim, '<li>$1</li>')
  
  // 换行（但不在代码块内）
  html = html.replace(/\n/g, '<br>')
  
  // 包装列表项
  html = html.replace(/(<li>.*?<\/li>)/gs, '<ul>$1</ul>')
  
  // 恢复代码块
  codeBlocks.forEach(({ placeholder, code, lang }) => {
    const escapedCode = code
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
    html = html.replace(placeholder, `<pre><code class="language-${lang}">${escapedCode}</code></pre>`)
  })
  
  return html
}

function downloadReport() {
  if (!props.reportContent) return
  
  const blob = new Blob([props.reportContent], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  
  const timestamp = new Date().toISOString().replace(/[:.]/g, '-').split('T')[0]
  link.download = `索引优化报告_${timestamp}.md`
  
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.index-optimization-report {
  width: 100%;
}

.markdown-content {
  padding: 15px;
  background: #f9f9f9;
  border-radius: 4px;
  max-height: 800px;
  overflow-y: auto;
}

.markdown-content :deep(h1),
.markdown-content :deep(h2),
.markdown-content :deep(h3) {
  margin-top: 20px;
  margin-bottom: 10px;
}

.markdown-content :deep(pre) {
  background: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  overflow-x: auto;
}

.markdown-content :deep(code) {
  background: #f5f5f5;
  padding: 2px 4px;
  border-radius: 2px;
  font-family: 'Courier New', monospace;
}

.json-content {
  background: #f5f5f5;
  padding: 15px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  max-height: 500px;
  overflow-y: auto;
}
</style>


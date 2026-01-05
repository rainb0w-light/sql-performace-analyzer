export function generateSingleMarkdownReport(result) {
  let md = '# SQL 性能分析报告\n\n'
  md += `> 生成时间：${new Date().toLocaleString('zh-CN')}\n\n`
  
  md += '## 📊 基本信息\n\n'
  md += `- **最终风险等级**：\`${result.finalRiskLevel || 'N/A'}\`\n`
  md += `- **处理时间**：${result.processingTimeMs ? (result.processingTimeMs / 1000).toFixed(2) + 's' : 'N/A'}\n\n`
  
  md += '## 📝 原始 SQL\n\n'
  md += '```sql\n' + result.originalSql + '\n```\n\n'
  
  if (result.histogramData && result.histogramData.length > 0) {
    md += '## 📊 直方图数据\n\n'
    md += '| 表名 | 列名 | 类型 | 桶数 | 最小值 | 最大值 | 采样数 |\n'
    md += '|------|------|------|------|--------|--------|--------|\n'
    result.histogramData.forEach(hist => {
      md += `| ${hist.tableName} | ${hist.columnName} | ${hist.histogramType} | ${hist.bucketCount} | ${hist.minValue} | ${hist.maxValue} | ${hist.sampleCount} |\n`
    })
    md += '\n'
  }
  
  if (result.predictorResult) {
    md += '## 🤖 Stage 1: LLM 预测结果\n\n'
    md += '### 预测指标\n\n'
    md += `- **风险等级**：\`${result.predictorResult.riskLevel}\`\n`
    md += `- **预估扫描行数**：${result.predictorResult.estimatedRowsExamined || 'N/A'}\n`
    md += `- **预期索引使用**：${result.predictorResult.expectedIndexUsage ? '✅ 是' : '❌ 否'}\n`
    md += `- **预期索引名**：${result.predictorResult.expectedIndexName || '无'}\n`
    md += `- **预期访问类型**：${result.predictorResult.expectedAccessType || 'N/A'}\n`
    md += `- **预估查询成本**：${result.predictorResult.estimatedQueryCost || 'N/A'}\n\n`
    
    md += '### 推理过程\n\n'
    md += result.predictorResult.reasoning + '\n\n'
    
    if (result.predictorResult.recommendations && result.predictorResult.recommendations.length > 0) {
      md += '### 初步建议\n\n'
      result.predictorResult.recommendations.forEach((rec, idx) => {
        md += `${idx + 1}. ${rec}\n`
      })
      md += '\n'
    }
  }
  
  if (result.fillingResult && result.fillingResult.scenarios) {
    md += '## 🎯 Stage 2: LLM 生成的多场景测试\n\n'
    md += '### LLM 推理过程\n\n'
    md += result.fillingResult.reasoning + '\n\n'
    
    md += '### 测试场景\n\n'
    result.fillingResult.scenarios.forEach((scenario, idx) => {
      md += `#### 场景 ${idx + 1}: ${scenario.scenarioName}\n\n`
      md += `**描述**：${scenario.description}\n\n`
      md += '**填充后的 SQL**：\n\n'
      md += '```sql\n' + scenario.filledSql + '\n```\n\n'
      md += '**使用的参数**：\n\n'
      md += '```json\n' + JSON.stringify(scenario.parameters, null, 2) + '\n```\n\n'
    })
  }
  
  if (result.scenarioVerifications && result.scenarioVerifications.length > 0) {
    md += '## ✅ Stage 3: 场景验证结果\n\n'
    result.scenarioVerifications.forEach((verification, idx) => {
      md += `### 场景 ${idx + 1}: ${verification.scenarioName}\n\n`
      if (verification.executionPlan && verification.executionPlan.queryBlock) {
        const table = verification.executionPlan.queryBlock.table
        const cost = verification.executionPlan.queryBlock.costInfo
        md += `- **访问类型**：${table.accessType}\n`
        md += `- **使用索引**：${table.key || '无'}\n`
        md += `- **实际扫描行数**：${table.rowsExaminedPerScan}\n`
        md += `- **查询成本**：${cost.queryCost}\n\n`
      }
    })
  }
  
  if (result.verificationComparison) {
    md += '## 🔍 验证对比分析\n\n'
    md += `**对比结果**：${result.verificationComparison.matched ? '✅ 预测一致' : '⚠️ 预测存在偏差'}\n\n`
    md += `**偏差严重程度**：\`${result.verificationComparison.deviationSeverity}\`\n\n`
    md += `**总结**：${result.verificationComparison.summary}\n\n`
    
    if (result.verificationComparison.details) {
      md += '### 详细对比\n\n'
      md += '| 指标 | 预测值 | 实际值 | 偏差 | 状态 |\n'
      md += '|------|--------|--------|------|------|\n'
      for (const [key, detail] of Object.entries(result.verificationComparison.details)) {
        const status = detail.matched ? '✅' : '❌'
        md += `| ${detail.metric} | ${detail.predictedValue} | ${detail.actualValue} | ${detail.deviation} | ${status} |\n`
      }
      md += '\n'
    }
  }
  
  if (result.refinementApplied && result.refinedResult) {
    md += '## 🔄 LLM 修正结果\n\n'
    md += '### 修正后指标\n\n'
    md += `- **修正后风险等级**：\`${result.refinedResult.riskLevel}\`\n`
    md += `- **修正后扫描行数**：${result.refinedResult.estimatedRowsExamined || 'N/A'}\n`
    md += `- **修正后索引使用**：${result.refinedResult.expectedIndexUsage ? '✅ 是' : '❌ 否'}\n`
    md += `- **修正后索引名**：${result.refinedResult.expectedIndexName || '无'}\n`
    md += `- **修正后访问类型**：${result.refinedResult.expectedAccessType || 'N/A'}\n`
    md += `- **修正后查询成本**：${result.refinedResult.estimatedQueryCost || 'N/A'}\n\n`
    
    md += '### 修正推理\n\n'
    md += result.refinedResult.reasoning + '\n\n'
  }
  
  if (result.recommendations && result.recommendations.length > 0) {
    md += '## 💡 最终优化建议\n\n'
    result.recommendations.forEach((rec, idx) => {
      md += `${idx + 1}. ${rec}\n`
    })
    md += '\n'
  }
  
  md += '---\n\n'
  md += '*本报告由 SQL Agent 智能分析系统自动生成*\n'
  
  return md
}

export function generateMapperMarkdownReport(mapperResult) {
  let md = '# Mapper XML 批量分析报告\n\n'
  md += `> 生成时间：${new Date().toLocaleString('zh-CN')}\n\n`
  
  md += '## 📋 基本信息\n\n'
  md += `- **Namespace**：\`${mapperResult.mapperNamespace}\`\n`
  md += `- **SQL 数量**：${mapperResult.results ? mapperResult.results.length : 0}\n`
  md += `- **总结**：${mapperResult.overallSummary || '已完成批量分析'}\n\n`
  
  if (mapperResult.results && mapperResult.results.length > 0) {
    const riskCounts = { LOW: 0, MEDIUM: 0, HIGH: 0, CRITICAL: 0 }
    mapperResult.results.forEach(r => {
      if (r.finalRiskLevel) {
        riskCounts[r.finalRiskLevel] = (riskCounts[r.finalRiskLevel] || 0) + 1
      }
    })
    
    md += '## 📊 风险等级分布\n\n'
    md += `- 🟢 **LOW（低风险）**：${riskCounts.LOW} 条\n`
    md += `- 🟡 **MEDIUM（中等风险）**：${riskCounts.MEDIUM} 条\n`
    md += `- 🟠 **HIGH（高风险）**：${riskCounts.HIGH} 条\n`
    md += `- 🔴 **CRITICAL（严重风险）**：${riskCounts.CRITICAL} 条\n\n`
  }
  
  if (mapperResult.results && mapperResult.results.length > 0) {
    md += '## 📝 详细分析\n\n'
    
    mapperResult.results.forEach((result, index) => {
      md += `### SQL #${index + 1}\n\n`
      md += `**风险等级**：\`${result.finalRiskLevel || 'N/A'}\` | `
      md += `**处理时间**：${result.processingTimeMs ? (result.processingTimeMs / 1000).toFixed(2) + 's' : 'N/A'}\n\n`
      
      md += '#### 原始 SQL\n\n'
      md += '```sql\n' + result.originalSql + '\n```\n\n'
      
      if (result.predictorResult) {
        md += '#### LLM 预测结果\n\n'
        md += `- **风险等级**：\`${result.predictorResult.riskLevel}\`\n`
        md += `- **预估扫描行数**：${result.predictorResult.estimatedRowsExamined || 'N/A'}\n`
        md += `- **预期索引使用**：${result.predictorResult.expectedIndexUsage ? '✅ 是' : '❌ 否'}\n`
        md += `- **预期索引名**：${result.predictorResult.expectedIndexName || '无'}\n\n`
      }
      
      if (result.scenarioVerifications && result.scenarioVerifications.length > 0) {
        md += `#### 验证结果（${result.scenarioVerifications.length} 个场景）\n\n`
        result.scenarioVerifications.forEach((v, vIdx) => {
          if (v.executionPlan && v.executionPlan.queryBlock) {
            const table = v.executionPlan.queryBlock.table
            md += `- **${v.scenarioName}**：访问类型=${table.accessType}，索引=${table.key || '无'}，扫描行数=${table.rowsExaminedPerScan}\n`
          }
        })
        md += '\n'
      }
      
      if (result.recommendations && result.recommendations.length > 0) {
        md += '#### 优化建议\n\n'
        result.recommendations.forEach((rec, rIdx) => {
          md += `${rIdx + 1}. ${rec}\n`
        })
        md += '\n'
      }
      
      md += '---\n\n'
    })
  }
  
  md += '*本报告由 SQL Agent 智能分析系统自动生成*\n'
  
  return md
}







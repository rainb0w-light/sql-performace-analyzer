<template>
  <div>
    <!-- 原始 SQL -->
    <el-card shadow="never" style="margin-bottom: 15px">
      <template #header>
        <span>📝 原始 SQL</span>
      </template>
      <pre class="code-block">{{ result.originalSql }}</pre>
    </el-card>

    <!-- 直方图数据 -->
    <el-collapse v-if="result.histogramData && result.histogramData.length > 0" style="margin-bottom: 15px">
      <el-collapse-item :title="`📊 直方图数据 (${result.histogramData.length} 列)`">
        <el-table :data="result.histogramData" border size="small">
          <el-table-column prop="tableName" label="表名" />
          <el-table-column prop="columnName" label="列名" />
          <el-table-column prop="histogramType" label="类型" />
          <el-table-column prop="bucketCount" label="桶数" />
          <el-table-column prop="minValue" label="最小值" />
          <el-table-column prop="maxValue" label="最大值" />
          <el-table-column prop="sampleCount" label="采样数" />
        </el-table>
      </el-collapse-item>
    </el-collapse>

    <!-- Stage 1: LLM 预测结果 -->
    <el-collapse v-if="result.predictorResult" style="margin-bottom: 15px">
      <el-collapse-item title="🤖 Stage 1: LLM 预测结果">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="风险等级">
            <el-tag :type="getRiskType(result.predictorResult.riskLevel)">
              {{ result.predictorResult.riskLevel }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="预估扫描行数">
            {{ result.predictorResult.estimatedRowsExamined || 'N/A' }}
          </el-descriptions-item>
          <el-descriptions-item label="预期索引使用">
            {{ result.predictorResult.expectedIndexUsage ? '✅ 是' : '❌ 否' }}
          </el-descriptions-item>
          <el-descriptions-item label="预期索引名">
            {{ result.predictorResult.expectedIndexName || '无' }}
          </el-descriptions-item>
          <el-descriptions-item label="预期访问类型">
            {{ result.predictorResult.expectedAccessType || 'N/A' }}
          </el-descriptions-item>
          <el-descriptions-item label="预估查询成本">
            {{ result.predictorResult.estimatedQueryCost || 'N/A' }}
          </el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 15px">
          <strong>推理过程：</strong>
          <p style="margin-top: 5px">{{ result.predictorResult.reasoning }}</p>
        </div>
        <div v-if="result.predictorResult.recommendations && result.predictorResult.recommendations.length > 0" style="margin-top: 15px">
          <strong>初步建议：</strong>
          <ul style="margin-top: 5px">
            <li v-for="(rec, rIdx) in result.predictorResult.recommendations" :key="rIdx">
              {{ rec }}
            </li>
          </ul>
        </div>
      </el-collapse-item>
    </el-collapse>

    <!-- Stage 2: LLM 生成的多场景测试 -->
    <el-collapse v-if="result.fillingResult" style="margin-bottom: 15px">
      <el-collapse-item :title="`🎯 Stage 2: LLM 生成的多场景测试 (${result.fillingResult.scenarios.length} 个场景)`">
        <div style="margin-bottom: 15px">
          <strong>LLM 推理过程：</strong>
          <p style="margin-top: 5px">{{ result.fillingResult.reasoning }}</p>
        </div>
        <el-card
          v-for="(scenario, sIdx) in result.fillingResult.scenarios"
          :key="sIdx"
          shadow="never"
          style="margin-bottom: 10px"
        >
          <h5>{{ sIdx + 1 }}. {{ scenario.scenarioName }}</h5>
          <p style="color: #666; font-size: 13px; margin-bottom: 10px">{{ scenario.description }}</p>
          <div style="margin-bottom: 10px">
            <strong>填充后的 SQL:</strong>
            <pre class="code-block">{{ scenario.filledSql }}</pre>
          </div>
          <div>
            <strong>使用的参数:</strong>
            <pre class="code-block">{{ JSON.stringify(scenario.parameters, null, 2) }}</pre>
          </div>
        </el-card>
      </el-collapse-item>
    </el-collapse>

    <!-- Stage 3: 场景验证结果 -->
    <el-collapse v-if="result.scenarioVerifications && result.scenarioVerifications.length > 0" style="margin-bottom: 15px">
      <el-collapse-item :title="`✅ Stage 3: 场景验证结果 (${result.scenarioVerifications.length} 个场景)`">
        <el-card
          v-for="(verification, vIdx) in result.scenarioVerifications"
          :key="vIdx"
          shadow="never"
          style="margin-bottom: 10px"
        >
          <h5>{{ vIdx + 1 }}. {{ verification.scenarioName }}</h5>
          <el-descriptions v-if="verification.executionPlan && verification.executionPlan.queryBlock" :column="2" border>
            <el-descriptions-item label="访问类型">
              {{ verification.executionPlan.queryBlock.table.accessType }}
            </el-descriptions-item>
            <el-descriptions-item label="使用索引">
              {{ verification.executionPlan.queryBlock.table.key || '无' }}
            </el-descriptions-item>
            <el-descriptions-item label="实际扫描行数">
              {{ verification.executionPlan.queryBlock.table.rowsExaminedPerScan }}
            </el-descriptions-item>
            <el-descriptions-item label="查询成本">
              {{ verification.executionPlan.queryBlock.costInfo.queryCost }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-collapse-item>
    </el-collapse>

    <!-- 验证对比分析 -->
    <el-collapse v-if="result.verificationComparison" style="margin-bottom: 15px">
      <el-collapse-item title="🔍 验证对比分析">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="对比结果">
            <el-tag :type="result.verificationComparison.matched ? 'success' : 'warning'">
              {{ result.verificationComparison.matched ? '✅ 预测一致' : '⚠️ 预测存在偏差' }}
            </el-tag>
            <el-tag
              :type="getRiskType(result.verificationComparison.deviationSeverity)"
              style="margin-left: 10px"
            >
              {{ result.verificationComparison.deviationSeverity }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总结">
            {{ result.verificationComparison.summary }}
          </el-descriptions-item>
        </el-descriptions>
        <div v-if="result.verificationComparison.details" style="margin-top: 15px">
          <strong>详细对比：</strong>
          <el-table :data="comparisonDetails" border style="margin-top: 10px">
            <el-table-column prop="metric" label="指标" />
            <el-table-column prop="predictedValue" label="预测值" />
            <el-table-column prop="actualValue" label="实际值" />
            <el-table-column prop="deviation" label="偏差" />
            <el-table-column label="状态">
              <template #default="{ row }">
                <el-tag :type="row.matched ? 'success' : 'danger'">
                  {{ row.matched ? '✅' : '❌' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-collapse-item>
    </el-collapse>

    <!-- LLM 修正结果 -->
    <el-collapse v-if="result.refinementApplied && result.refinedResult" style="margin-bottom: 15px">
      <el-collapse-item title="🔄 LLM 修正结果">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="修正后风险等级">
            <el-tag :type="getRiskType(result.refinedResult.riskLevel)">
              {{ result.refinedResult.riskLevel }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="修正后扫描行数">
            {{ result.refinedResult.estimatedRowsExamined || 'N/A' }}
          </el-descriptions-item>
          <el-descriptions-item label="修正后索引使用">
            {{ result.refinedResult.expectedIndexUsage ? '✅ 是' : '❌ 否' }}
          </el-descriptions-item>
          <el-descriptions-item label="修正后索引名">
            {{ result.refinedResult.expectedIndexName || '无' }}
          </el-descriptions-item>
          <el-descriptions-item label="修正后访问类型">
            {{ result.refinedResult.expectedAccessType || 'N/A' }}
          </el-descriptions-item>
          <el-descriptions-item label="修正后查询成本">
            {{ result.refinedResult.estimatedQueryCost || 'N/A' }}
          </el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 15px">
          <strong>修正推理：</strong>
          <p style="margin-top: 5px">{{ result.refinedResult.reasoning }}</p>
        </div>
      </el-collapse-item>
    </el-collapse>

    <!-- 最终建议 -->
    <el-card v-if="result.recommendations && result.recommendations.length > 0" shadow="never">
      <template #header>
        <span>💡 最终优化建议</span>
      </template>
      <ul>
        <li v-for="(rec, rIdx) in result.recommendations" :key="rIdx" style="margin-bottom: 8px">
          {{ rec }}
        </li>
      </ul>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  result: {
    type: Object,
    required: true
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

const comparisonDetails = computed(() => {
  if (!props.result.verificationComparison?.details) return []
  return Object.entries(props.result.verificationComparison.details).map(([key, detail]) => ({
    metric: detail.metric,
    predictedValue: detail.predictedValue,
    actualValue: detail.actualValue,
    deviation: detail.deviation,
    matched: detail.matched
  }))
})
</script>

<style scoped>
.code-block {
  background: #f5f5f5;
  padding: 12px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  border-left: 4px solid #667eea;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-word;
  margin: 0;
}
</style>



<template>
  <div class="sql-agent-analysis">
    <el-card>
      <template #header>
        <div class="header">
          <h1>🤖 SQL Agent 智能分析</h1>
          <p>基于链式工作流的 SQL 性能智能分析系统</p>
        </div>
      </template>

      <AgentAnalysisTabs>
        <template #single>
          <SingleSqlAnalysis />
        </template>
        <template #mapper>
          <MapperBatchAnalysis />
        </template>
      </AgentAnalysisTabs>
    </el-card>
  </div>
</template>

<script setup>
import AgentAnalysisTabs from '@/components/sql-agent/AgentAnalysisTabs.vue'
import SingleSqlAnalysis from '@/components/sql-agent/SingleSqlAnalysis.vue'
import MapperBatchAnalysis from '@/components/sql-agent/MapperBatchAnalysis.vue'
</script>

<style scoped>
.sql-agent-analysis {
  width: 100%;
}

.header {
  text-align: center;
}

.header h1 {
  color: #333;
  margin-bottom: 10px;
  font-size: 28px;
}

.header p {
  color: #666;
  font-size: 16px;
}
</style>



<template>
  <div class="table-analysis">
    <el-card>
      <template #header>
        <div class="header">
          <h1>单表综合分析工具</h1>
        </div>
      </template>

      <TableAnalysisTabs @tab-change="handleTabChange">
        <template #upload>
          <MapperUpload />
        </template>
        <template #analyze>
          <TableAnalyzer />
        </template>
      </TableAnalysisTabs>
    </el-card>
  </div>
</template>

<script setup>
import TableAnalysisTabs from '@/components/table-analysis/TableAnalysisTabs.vue'
import MapperUpload from '@/components/table-analysis/MapperUpload.vue'
import TableAnalyzer from '@/components/table-analysis/TableAnalyzer.vue'

function handleTabChange(name) {
  // 可以在这里处理标签页切换逻辑
  console.log('切换到标签页:', name)
}
</script>

<style scoped>
.table-analysis {
  width: 100%;
}

.header h1 {
  color: #333;
  margin: 0;
  font-size: 24px;
}
</style>



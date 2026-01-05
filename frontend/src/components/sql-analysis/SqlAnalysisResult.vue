<template>
  <el-card v-if="report" style="margin-top: 20px">
    <template #header>
      <div class="card-header">
        <span>分析报告</span>
      </div>
    </template>

    <div
      id="report-content"
      class="report-content markdown-body"
      v-html="renderedReport"
    />
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { parseMarkdown } from '@/utils/markdown'

const props = defineProps({
  report: {
    type: String,
    default: ''
  }
})

const renderedReport = computed(() => {
  return parseMarkdown(props.report)
})
</script>

<style scoped>
.report-content {
  background: #fafafa;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  padding: 20px;
  min-height: 200px;
}
</style>

<style>
@import '@/styles/main.css';
</style>







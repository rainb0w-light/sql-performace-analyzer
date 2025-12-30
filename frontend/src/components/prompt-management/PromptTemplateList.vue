<template>
  <div>
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="8">
        <el-statistic title="模板总数" :value="templates.length" />
      </el-col>
      <el-col :span="8">
        <el-statistic title="已修改" :value="editedCount" />
      </el-col>
    </el-row>

    <el-table
      :data="templates"
      highlight-current-row
      :current-row-key="selectedTemplate?.id"
      @current-change="handleSelect"
      style="width: 100%"
      empty-text="暂无模板，系统将自动初始化默认模板"
    >
      <el-table-column prop="templateType" label="类型" width="120">
        <template #default="{ row }">
          <el-tag :type="getTemplateType(row.templateType)">
            {{ row.templateType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="templateName" label="模板名称" min-width="150" />
      <el-table-column prop="description" label="描述" min-width="200">
        <template #default="{ row }">
          {{ row.description || '无描述' }}
        </template>
      </el-table-column>
      <el-table-column prop="gmtCreated" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.gmtCreated) }}
        </template>
      </el-table-column>
      <el-table-column prop="gmtModified" label="更新时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.gmtModified) }}
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  templates: {
    type: Array,
    default: () => []
  },
  selectedTemplate: {
    type: Object,
    default: null
  },
  editedTemplates: {
    type: Set,
    default: () => new Set()
  }
})

const emit = defineEmits(['select'])

const editedCount = computed(() => props.editedTemplates.size)

function getTemplateType(type) {
  const typeMap = {
    MYSQL: 'success',
    GOLDENDB: 'warning',
    POSTGRESQL: 'info'
  }
  return typeMap[type] || ''
}

function handleSelect(template) {
  if (template) {
    emit('select', template)
  }
}

function formatDate(dateString) {
  if (!dateString) return '未知'
  const date = new Date(dateString)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<style scoped>
:deep(.el-table__row) {
  cursor: pointer;
}

:deep(.el-table__row:hover) {
  background-color: #f5f7fa;
}
</style>



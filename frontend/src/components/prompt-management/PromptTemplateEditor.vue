<template>
  <el-card v-if="template">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>编辑模板: {{ template.templateName }}</span>
        <div>
          <el-button
            type="primary"
            @click="handleSave"
            :loading="saving"
            :disabled="!isModified"
          >
            {{ saving ? '保存中...' : '保存更改' }}
          </el-button>
          <el-button
            @click="handleReset"
            :disabled="saving || !isModified"
          >
            重置
          </el-button>
        </div>
      </div>
    </template>

    <el-alert
      type="info"
      :closable="false"
      style="margin-bottom: 20px"
    >
      <template #title>
        <div>
          <strong>模板变量说明</strong>
          <p style="margin-top: 5px; font-size: 12px">
            模板支持以下变量，系统会自动替换：
            <strong>{sql}</strong> - SQL 语句，
            <strong>{execution_plan}</strong> - 执行计划，
            <strong>{table_structures}</strong> - 表结构信息
          </p>
        </div>
      </template>
    </el-alert>

    <el-alert
      v-if="successMessage"
      :title="successMessage"
      type="success"
      :closable="false"
      show-icon
      style="margin-bottom: 20px"
    />

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: 20px"
    />

    <el-input
      v-model="editingContent"
      type="textarea"
      :rows="20"
      placeholder="请输入模板内容..."
      @input="handleContentChange"
    />
  </el-card>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { updatePrompt } from '@/api'

const props = defineProps({
  template: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['save', 'reset'])

const saving = ref(false)
const successMessage = ref('')
const errorMessage = ref('')
const editingContent = ref('')
const originalContent = ref('')

const isModified = computed(() => {
  return props.template && editingContent.value !== originalContent.value
})

watch(() => props.template, (newTemplate) => {
  if (newTemplate) {
    editingContent.value = newTemplate.templateContent
    originalContent.value = newTemplate.templateContent
    successMessage.value = ''
    errorMessage.value = ''
  }
}, { immediate: true })

function handleContentChange() {
  // 内容变化时清除消息
  if (successMessage.value) {
    successMessage.value = ''
  }
  if (errorMessage.value) {
    errorMessage.value = ''
  }
}

async function handleSave() {
  if (!props.template || !isModified.value) {
    return
  }

  saving.value = true
  successMessage.value = ''
  errorMessage.value = ''

  try {
    const updatedTemplate = await updatePrompt(props.template.templateType, editingContent.value)
    originalContent.value = updatedTemplate.templateContent
    editingContent.value = updatedTemplate.templateContent
    
    successMessage.value = '模板保存成功！新的模板将在下次分析时生效。'
    
    emit('save', updatedTemplate)
    
    setTimeout(() => {
      successMessage.value = ''
    }, 3000)
  } catch (err) {
    errorMessage.value = '保存失败: ' + err.message
    console.error('保存模板错误:', err)
  } finally {
    saving.value = false
  }
}

function handleReset() {
  if (props.template) {
    editingContent.value = originalContent.value
    successMessage.value = ''
    errorMessage.value = ''
    emit('reset')
  }
}
</script>









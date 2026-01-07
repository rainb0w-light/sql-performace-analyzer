<template>
  <el-dialog
    v-model="visible"
    title="新增 Prompt 模板"
    width="800px"
    @close="handleClose"
  >
    <el-form :model="form" label-width="150px">
      <el-form-item label="模板类型（唯一标识）">
        <el-input
          v-model="form.templateType"
          placeholder="例如: POSTGRESQL"
          :disabled="creating"
        />
        <div style="font-size: 12px; color: #999; margin-top: 5px">
          模板类型必须唯一，建议使用大写字母
        </div>
      </el-form-item>

      <el-form-item label="模板名称">
        <el-input
          v-model="form.templateName"
          placeholder="例如: PostgreSQL性能分析专家"
          :disabled="creating"
        />
      </el-form-item>

      <el-form-item label="模板描述（可选）">
        <el-input
          v-model="form.description"
          placeholder="模板的简要描述"
          :disabled="creating"
        />
      </el-form-item>

      <el-form-item label="模板内容">
        <el-input
          v-model="form.templateContent"
          type="textarea"
          :rows="15"
          placeholder="请输入模板内容，支持变量：{sql}、{execution_plan}、{table_structures}"
          :disabled="creating"
        />
        <div style="font-size: 12px; color: #999; margin-top: 5px">
          支持的变量：<strong>{sql}</strong> - SQL 语句，<strong>{execution_plan}</strong> - 执行计划，<strong>{table_structures}</strong> - 表结构信息
        </div>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      :closable="false"
      show-icon
      style="margin-bottom: 20px"
    />

    <template #footer>
      <el-button @click="handleClose" :disabled="creating">取消</el-button>
      <el-button
        type="primary"
        @click="handleCreate"
        :loading="creating"
        :disabled="!isValid"
      >
        {{ creating ? '创建中...' : '创建模板' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { createPrompt } from '@/api'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'created'])

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const creating = ref(false)
const errorMessage = ref('')

const form = reactive({
  templateType: '',
  templateName: '',
  templateContent: '',
  description: ''
})

const isValid = computed(() => {
  return form.templateType.trim() !== '' &&
         form.templateName.trim() !== '' &&
         form.templateContent.trim() !== ''
})

function handleClose() {
  if (creating.value) return
  visible.value = false
  resetForm()
}

function resetForm() {
  form.templateType = ''
  form.templateName = ''
  form.templateContent = ''
  form.description = ''
  errorMessage.value = ''
}

async function handleCreate() {
  if (!isValid.value) {
    errorMessage.value = '请填写所有必填字段'
    return
  }

  creating.value = true
  errorMessage.value = ''

  try {
    const createdTemplate = await createPrompt({
      templateType: form.templateType.trim().toUpperCase(),
      templateName: form.templateName.trim(),
      templateContent: form.templateContent.trim(),
      description: form.description.trim() || ''
    })

    emit('created', createdTemplate)
    handleClose()
  } catch (err) {
    errorMessage.value = '创建失败: ' + err.message
    console.error('创建模板错误:', err)
  } finally {
    creating.value = false
  }
}
</script>









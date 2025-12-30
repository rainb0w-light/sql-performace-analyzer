<template>
  <div class="prompt-management">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h1>Prompt 模板管理</h1>
          <el-button type="primary" @click="showCreateModal = true">新增模板</el-button>
        </div>
      </template>

      <el-loading v-if="loading && templates.length === 0" text="加载中..." />

      <div v-else>
        <div class="template-list-container">
          <PromptTemplateList
            :templates="templates"
            :selected-template="selectedTemplate"
            :edited-templates="editedTemplates"
            @select="handleSelectTemplate"
          />
        </div>
        <div class="template-editor-container">
          <PromptTemplateEditor
            :template="selectedTemplate"
            @save="handleSave"
            @reset="handleReset"
          />
        </div>
      </div>
    </el-card>

    <CreateTemplateModal
      v-model="showCreateModal"
      @created="handleTemplateCreated"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getPrompts } from '@/api'
import PromptTemplateList from '@/components/prompt-management/PromptTemplateList.vue'
import PromptTemplateEditor from '@/components/prompt-management/PromptTemplateEditor.vue'
import CreateTemplateModal from '@/components/prompt-management/CreateTemplateModal.vue'

const loading = ref(false)
const templates = ref([])
const selectedTemplate = ref(null)
const editedTemplates = ref(new Set())
const showCreateModal = ref(false)

onMounted(async () => {
  await loadTemplates()
})

async function loadTemplates() {
  loading.value = true
  try {
    templates.value = await getPrompts()
    if (templates.value.length > 0 && !selectedTemplate.value) {
      selectedTemplate.value = templates.value[0]
    }
  } catch (err) {
    console.error('加载模板列表错误:', err)
  } finally {
    loading.value = false
  }
}

function handleSelectTemplate(template) {
  if (editedTemplates.value.has(selectedTemplate.value?.id)) {
    // 如果有未保存的更改，提示用户
    // 这里简化处理，直接切换
  }
  selectedTemplate.value = template
}

function handleSave(updatedTemplate) {
  const index = templates.value.findIndex(t => t.id === updatedTemplate.id)
  if (index !== -1) {
    templates.value[index] = updatedTemplate
  }
  selectedTemplate.value = updatedTemplate
  editedTemplates.value.delete(updatedTemplate.id)
}

function handleReset() {
  if (selectedTemplate.value) {
    editedTemplates.value.delete(selectedTemplate.value.id)
  }
}

async function handleTemplateCreated(createdTemplate) {
  await loadTemplates()
  selectedTemplate.value = createdTemplate
}
</script>

<style scoped>
.prompt-management {
  width: 100%;
}

h1 {
  color: #333;
  font-size: 24px;
  margin: 0;
}

.template-list-container {
  margin-bottom: 20px;
}

.template-editor-container {
  width: 100%;
}
</style>



<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>上传MyBatis Mapper XML文件</span>
      </div>
    </template>

    <el-form :model="form" label-width="120px">
      <el-form-item label="Mapper命名空间：">
        <el-input
          v-model="form.mapperNamespace"
          placeholder="例如: com.example.mapper.UserMapper"
          :disabled="uploading"
        />
      </el-form-item>

      <el-form-item label="XML内容：">
        <el-input
          v-model="form.xmlContent"
          type="textarea"
          :rows="10"
          placeholder="粘贴MyBatis Mapper XML内容..."
          :disabled="uploading"
        />
      </el-form-item>

      <el-form-item>
        <el-button
          type="primary"
          @click="handleUpload"
          :loading="uploading"
          :disabled="!form.mapperNamespace.trim() || !form.xmlContent.trim()"
        >
          {{ uploading ? '上传中...' : '上传并解析' }}
        </el-button>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      style="margin-top: 20px"
    />

    <el-alert
      v-if="success"
      :title="success"
      type="success"
      :closable="false"
      show-icon
      style="margin-top: 20px"
    />
  </el-card>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { uploadMapperXml } from '@/api'

const uploading = ref(false)
const error = ref(null)
const success = ref(null)

const form = reactive({
  mapperNamespace: '',
  xmlContent: ''
})

async function handleUpload() {
  if (!form.mapperNamespace.trim() || !form.xmlContent.trim()) {
    error.value = '请填写Mapper命名空间和XML内容'
    return
  }

  uploading.value = true
  error.value = null
  success.value = null

  try {
    const data = await uploadMapperXml({
      mapperNamespace: form.mapperNamespace.trim(),
      xmlContent: form.xmlContent.trim()
    })

    success.value = `成功解析 ${data.queryCount} 个SQL查询！`
    form.xmlContent = ''
  } catch (err) {
    error.value = err.message || '上传失败，请检查网络连接或稍后重试'
    console.error('上传错误:', err)
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.card-header {
  font-weight: 600;
  font-size: 18px;
}
</style>



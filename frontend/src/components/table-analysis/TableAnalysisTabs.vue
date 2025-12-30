<template>
  <el-tabs v-model="activeTab" @tab-change="handleTabChange">
    <el-tab-pane label="上传MyBatis XML" name="upload">
      <slot name="upload" />
    </el-tab-pane>
    <el-tab-pane label="表分析" name="analyze">
      <slot name="analyze" />
    </el-tab-pane>
    <el-tab-pane label="参数管理" name="parameters">
      <slot name="parameters" />
    </el-tab-pane>
  </el-tabs>
</template>

<script setup>
import { ref } from 'vue'

const activeTab = ref('upload')

const emit = defineEmits(['tab-change'])

function handleTabChange(name) {
  emit('tab-change', name)
}
</script>



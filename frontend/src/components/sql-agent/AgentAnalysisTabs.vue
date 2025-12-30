<template>
  <el-tabs v-model="activeTab">
    <el-tab-pane label="单条 SQL 分析" name="single">
      <slot name="single" />
    </el-tab-pane>
    <el-tab-pane label="Mapper XML 批量分析" name="mapper">
      <slot name="mapper" />
    </el-tab-pane>
  </el-tabs>
</template>

<script setup>
import { ref } from 'vue'

const activeTab = ref('single')
</script>



<template>
  <el-menu
    :default-active="activeIndex"
    class="navbar"
    mode="horizontal"
    :ellipsis="false"
    router
  >
    <div class="navbar-brand">
      <el-icon><DataAnalysis /></el-icon>
      <span>SQL 性能分析工具</span>
    </div>
    <div class="flex-grow" />
    <el-menu-item index="/">
      <el-icon><House /></el-icon>
      <span>SQL 分析</span>
    </el-menu-item>
    <el-menu-item index="/sql-agent-analysis">
      <el-icon><Cpu /></el-icon>
      <span>Agent 分析</span>
    </el-menu-item>
    <el-menu-item index="/table-analysis">
      <el-icon><Document /></el-icon>
      <span>表分析</span>
    </el-menu-item>
    <el-menu-item index="/prompt-management">
      <el-icon><Setting /></el-icon>
      <span>模板管理</span>
    </el-menu-item>
  </el-menu>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, House, Cpu, Document, Setting } from '@element-plus/icons-vue'

const route = useRoute()

const activeIndex = computed(() => route.path)
</script>

<style scoped>
.navbar {
  background: #409EFF;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
  border: none;
}

.navbar :deep(.el-menu-item) {
  color: rgba(255, 255, 255, 0.9);
  border-bottom: 3px solid transparent;
}

.navbar :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.15);
  color: white;
}

.navbar :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.25);
  color: white;
  border-bottom-color: white;
}

.navbar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  color: white;
  font-size: 20px;
  font-weight: 700;
  padding: 0 20px;
  cursor: pointer;
}

.navbar-brand:hover {
  opacity: 0.9;
}

.flex-grow {
  flex: 1;
}
</style>



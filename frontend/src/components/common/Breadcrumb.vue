<template>
  <el-breadcrumb class="breadcrumb" separator="/">
    <el-breadcrumb-item
      v-for="(item, index) in breadcrumbItems"
      :key="index"
      :to="index === 0 ? '/' : undefined"
    >
      {{ item }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

const breadcrumbItems = computed(() => {
  return route.meta.breadcrumb || ['首页']
})
</script>

<style scoped>
.breadcrumb {
  background: white;
  border-bottom: 1px solid #e0e0e0;
  padding: 12px 20px;

}
</style>



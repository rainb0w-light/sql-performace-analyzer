<template>
  <Layout>
    <router-view />
  </Layout>
</template>

<script setup>
import Layout from './components/common/Layout.vue'
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  background: #f5f5f5;
  line-height: 1.6;
}

#app {
  min-height: 100vh;
}
</style>







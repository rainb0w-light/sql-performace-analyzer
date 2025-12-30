<template>
  <div class="layout">
    <NavBar />
    <Breadcrumb />
    <main class="main-content">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import NavBar from './NavBar.vue'
import Breadcrumb from './Breadcrumb.vue'
</script>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

 .main-content {
  //flex: 1;
  //max-width: 1400px;
  margin: 0 auto;
  padding: 30px 20px;
  width: 100%;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>



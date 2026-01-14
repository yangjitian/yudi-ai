<template>
  <div class="app-background">
    <div class="floating-circles">
      <div class="floating-circle fc-1"></div>
      <div class="floating-circle fc-2"></div>
      <div class="floating-circle fc-3"></div>
    </div>
  </div>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

onMounted(async () => {
  await userStore.restoreUserState()
})
</script>

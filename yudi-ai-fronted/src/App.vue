<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// 应用启动时恢复登录状态
onMounted(async () => {
  await userStore.restoreUserState()
})
</script>

<style scoped>
</style>



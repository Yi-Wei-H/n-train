<template>
  <el-config-provider :message="messageConfig">
    <router-view />
  </el-config-provider>
</template>

<script setup>
const messageConfig = { showClose: true, grouping: true }
</script>

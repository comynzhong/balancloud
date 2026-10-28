<template>
  <router-view />
</template>

<script setup>
import useSettingsStore from '@/store/modules/settings'
import useRuntimeStore from '@/store/modules/runtime'
import { handleThemeStyle } from '@/utils/theme'

const runtimeStore = useRuntimeStore()

onMounted(() => {
  runtimeStore.load()
  nextTick(() => {
    // 初始化主题样式
    handleThemeStyle(useSettingsStore().theme)
  })
})
</script>

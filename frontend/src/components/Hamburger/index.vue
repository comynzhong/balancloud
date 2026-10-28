<template>
  <div class="hamburger" @click="toggleClick">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
      <path d="M4 6.5h16M4 12h16M4 17.5h16" />
    </svg>
  </div>
</template>

<script setup>
const emit = defineEmits()
const toggleClick = () => {
  emit('toggleClick');
}
</script>

<style scoped>
.hamburger {
  display: grid;
  place-items: center;
}
</style>
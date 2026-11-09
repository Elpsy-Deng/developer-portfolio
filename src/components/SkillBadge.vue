<script setup lang="ts">
defineProps<{ label: string }>()
</script>
<template>
  <span class="skill-badge">{{ label }}</span>
</template>

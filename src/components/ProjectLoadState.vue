<script setup lang="ts">
defineProps<{ loading: boolean; error: string }>()
defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading" class="catalog-state" role="status" aria-live="polite">正在載入作品…</div>
  <div v-else-if="error" class="catalog-state">
    <p role="alert">{{ error }}</p>
    <button class="button button-outline" @click="$emit('retry')">重新載入作品</button>
  </div>
</template>

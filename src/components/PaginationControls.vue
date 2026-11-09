<script setup lang="ts">
defineProps<{ page: number; pageSize: number; total: number; totalPages: number; disabled?: boolean }>()
const emit = defineEmits<{ page: [value: number]; size: [value: number] }>()
</script>
<template>
  <nav class="pagination-controls" aria-label="分頁控制">
    <p>第 {{ page }} 頁 · 共 {{ totalPages }} 頁／{{ total }} 筆</p>
    <div class="admin-actions">
      <button class="button button-outline" :disabled="disabled || page <= 1" @click="emit('page', 1)">第一頁</button>
      <button class="button button-outline" :disabled="disabled || page <= 1" @click="emit('page', page - 1)">上一頁</button>
      <button class="button button-outline" :disabled="disabled || page >= totalPages" @click="emit('page', page + 1)">下一頁</button>
      <label>每頁筆數<select :value="pageSize" :disabled="disabled" @change="emit('size', Number(($event.target as HTMLSelectElement).value))"><option v-for="size in [1,2,6,12,24,50]" :key="size" :value="size">{{ size }}</option></select></label>
    </div>
  </nav>
</template>

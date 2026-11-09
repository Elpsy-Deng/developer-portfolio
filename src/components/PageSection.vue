<script setup lang="ts">
defineProps<{ title: string; eyebrow?: string; description?: string; id?: string }>()
</script>
<template>
  <section :id="id" class="page-section">
    <div class="section-heading">
      <div>
        <p v-if="eyebrow" class="eyebrow">{{ eyebrow }}</p>
        <h2>{{ title }}</h2>
        <p v-if="description" class="section-description">{{ description }}</p>
      </div>
      <slot name="action" />
    </div>
    <slot />
  </section>
</template>

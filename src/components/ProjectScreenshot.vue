<script setup lang="ts">
import type { ProjectScreenshot } from '../types/project'
import { computed } from 'vue'
import ProjectMediaPlaceholder from './ProjectMediaPlaceholder.vue'

const props = defineProps<{ screenshot: ProjectScreenshot }>()
const architecture = computed(() => props.screenshot.title.startsWith('架構說明圖'))
</script>

<template>
  <figure v-if="screenshot.src" class="media-placeholder media-screenshot">
    <a
      :href="screenshot.src"
      target="_blank"
      rel="noopener noreferrer"
      :aria-label="`開啟完整圖片：${screenshot.title}（新分頁）`"
    >
      <img :src="screenshot.src" :alt="screenshot.alt ?? screenshot.title" loading="lazy" />
    </a>
    <figcaption>
      <span class="placeholder-label">{{ architecture ? 'ARCHITECTURE ILLUSTRATION' : 'SAVED SCREENSHOT' }} / 點選查看完整圖片</span>
      <h3>{{ screenshot.title }}</h3>
      <p>{{ screenshot.description }}</p>
    </figcaption>
  </figure>
  <ProjectMediaPlaceholder v-else :title="screenshot.title" :description="screenshot.description" />
</template>

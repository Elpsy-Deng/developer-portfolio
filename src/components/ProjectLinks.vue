<script setup lang="ts">
import AppIcon from './AppIcon.vue'
const publicShowcase = import.meta.env.VITE_PUBLIC_SHOWCASE === 'true'
defineProps<{ githubUrl: string | null; liveDemoUrl: string | null; localDemoUrl?: string }>()
</script>
<template>
  <div class="project-links">
    <template
      v-for="link in [
        { label: 'GitHub', url: githubUrl, icon: 'github' as const },
        { label: 'Live Demo', url: publicShowcase ? null : liveDemoUrl, icon: 'external' as const },
      ]"
      :key="link.label"
    >
      <a
        v-if="link.url"
        :href="link.url"
        target="_blank"
        rel="noopener noreferrer"
        class="button button-outline"
        :aria-label="`${link.label}（在新分頁開啟）`"
        ><AppIcon :name="link.icon" :size="18" />{{ link.label
        }}<AppIcon name="external" :size="14"
      /></a>
      <span v-else class="unavailable-link" :aria-label="`${link.label} 連結尚未提供`"
        ><AppIcon :name="link.icon" :size="18" />{{ link.label }}<small>{{ publicShowcase && link.label === 'Live Demo' ? '私人展示需授權' : '待補' }}</small></span
      >
    </template>
    <p v-if="publicShowcase" class="local-demo-note">
      操作 Demo 為私人展示，需 Tailscale 授權；原始碼與驗證文件可能需要 GitHub 存取權。
    </p>
    <p v-else class="local-demo-note">
      原始碼與驗證文件可能需要 GitHub 存取權；公開 Demo 連結尚待提供。
    </p>
    <a
      v-if="!publicShowcase && localDemoUrl"
      :href="localDemoUrl"
      target="_blank"
      rel="noopener noreferrer"
      class="button button-outline"
      aria-label="本機 Demo（需啟動服務，在新分頁開啟）"
    >
      <AppIcon name="external" :size="18" />本機 Demo
    </a>
    <p v-if="!publicShowcase && localDemoUrl" class="local-demo-note">
      本機連結 · 需先依專案 README 啟動前後端，尚未公開部署。
    </p>
  </div>
</template>

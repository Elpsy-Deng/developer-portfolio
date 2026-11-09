<script setup lang="ts">
import AppIcon from '../components/AppIcon.vue'
withDefaults(defineProps<{ kind?: 'page' | 'project' }>(), { kind: 'page' })
</script>
<template>
  <div class="container not-found">
    <p class="eyebrow">404 / A SMALL DETOUR</p>
    <span class="not-found-number" aria-hidden="true">404<span>.</span></span>
    <h1>{{ kind === 'project' ? '找不到這個專案' : '這個頁面還不存在' }}</h1>
    <p>連結可能有誤，回到作品列表繼續探索吧。</p>
    <RouterLink to="/projects" class="button button-dark"
      >瀏覽所有專案<AppIcon name="arrow" :size="18"
    /></RouterLink>
  </div>
</template>

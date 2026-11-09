<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import SiteNav from './components/SiteNav.vue'
import SiteFooter from './components/SiteFooter.vue'
const route = useRoute()
const mainContent = ref<HTMLElement | null>(null)
watch(
  () => route.path,
  async () => {
    await nextTick()
    mainContent.value?.focus({ preventScroll: true })
  },
)
</script>

<template>
  <a class="skip-link" href="#main-content">跳至主要內容</a>
  <SiteNav />
  <main ref="mainContent" id="main-content" tabindex="-1"><RouterView /></main>
  <SiteFooter />
</template>

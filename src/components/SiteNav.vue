<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppIcon from './AppIcon.vue'
const route = useRoute()
const menuOpen = ref(false)
const menuButton = ref<HTMLButtonElement | null>(null)
const links = [
  { to: '/', label: '首頁', number: '01' },
  { to: '/projects', label: '專案作品', number: '02' },
  { to: '/about', label: '關於我', number: '03' },
]
watch(
  () => route.fullPath,
  () => {
    menuOpen.value = false
  },
)
function closeMenu() {
  menuOpen.value = false
  menuButton.value?.focus()
}
</script>

<template>
  <header class="site-header" @keydown.esc="closeMenu">
    <div class="container nav-inner">
      <RouterLink to="/" class="brand" aria-label="Devfolio 首頁"
        ><span class="brand-mark"><AppIcon name="code" :size="21" /></span>devfolio<span
          class="brand-dot"
          >.</span
        ></RouterLink
      >
      <nav class="desktop-nav" aria-label="主要導覽">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          :class="{
            'nav-active': link.to === '/' ? route.path === '/' : route.path.startsWith(link.to),
          }"
          ><span>{{ link.number }}</span
          >{{ link.label }}</RouterLink
        >
      </nav>
      <span class="nav-caption">BUILD WITH INTENTION</span>
      <button
        ref="menuButton"
        class="menu-toggle"
        :aria-expanded="menuOpen"
        aria-controls="mobile-navigation"
        :aria-label="menuOpen ? '關閉導覽選單' : '開啟導覽選單'"
        @click="menuOpen = !menuOpen"
      >
        <AppIcon :name="menuOpen ? 'close' : 'menu'" />
      </button>
    </div>
    <nav v-if="menuOpen" id="mobile-navigation" class="mobile-nav container" aria-label="手機導覽">
      <RouterLink v-for="link in links" :key="link.to" :to="link.to"
        >{{ link.label }}<AppIcon name="arrow" :size="18"
      /></RouterLink>
    </nav>
  </header>
</template>

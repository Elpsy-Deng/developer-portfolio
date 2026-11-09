<script setup lang="ts">
const publicShowcase = import.meta.env.VITE_PUBLIC_SHOWCASE === 'true'
</script>
<template>
  <footer class="site-footer">
    <div class="container footer-inner">
      <RouterLink to="/" class="footer-brand">devfolio<span>.</span></RouterLink>
      <p>用程式實現想法，讓細節成就體驗。</p>
      <RouterLink v-if="!publicShowcase" to="/admin" class="admin-entry">管理入口</RouterLink>
      <span class="footer-note">© {{ new Date().getFullYear() }} · Developer Portfolio</span>
    </div>
  </footer>
</template>

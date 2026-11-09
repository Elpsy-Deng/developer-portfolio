<script setup lang="ts">
withDefaults(
  defineProps<{
    name:
      | 'arrow'
      | 'external'
      | 'code'
      | 'search'
      | 'menu'
      | 'close'
      | 'pin'
      | 'image'
      | 'layers'
      | 'check'
      | 'github'
    size?: number
  }>(),
  { size: 20 },
)
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-if="name === 'arrow'" d="M5 12h14m-6-6 6 6-6 6" />
    <path v-else-if="name === 'external'" d="M14 4h6v6m0-6L10 14m-1-9H4v15h15v-5" />
    <path v-else-if="name === 'code'" d="m8 6-6 6 6 6m8-12 6 6-6 6M14 3l-4 18" />
    <template v-else-if="name === 'search'">
      <circle cx="10.5" cy="10.5" r="6.5" />
      <path d="m16 16 5 5" />
    </template>
    <path v-else-if="name === 'menu'" d="M4 6h16M4 12h16M4 18h16" />
    <path v-else-if="name === 'close'" d="m6 6 12 12M18 6 6 18" />
    <template v-else-if="name === 'pin'">
      <path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z" />
      <circle cx="12" cy="10" r="2.5" />
    </template>
    <template v-else-if="name === 'image'">
      <rect x="3" y="3" width="18" height="18" rx="3" />
      <circle cx="8" cy="8" r="1.5" />
      <path d="m3 17 5-5 4 4 4-7 5 8" />
    </template>
    <path
      v-else-if="name === 'layers'"
      d="m12 3 10 6-10 6L2 9l10-6ZM2 13l10 6 10-6M2 17l10 6 10-6"
    />
    <path v-else-if="name === 'check'" d="m5 12 4 4L19 6" />
    <path
      v-else-if="name === 'github'"
      d="M9 19c-5 1-5-3-7-3m14 6v-4a3.5 3.5 0 0 0-1-2.5c3-.3 6-1.4 6-6a4.7 4.7 0 0 0-1.3-3.3 4.4 4.4 0 0 0-.1-3.2s-1.2-.4-3.6 1.3a12 12 0 0 0-6 0C7.6 2.6 6.4 3 6.4 3a4.4 4.4 0 0 0-.1 3.2A4.7 4.7 0 0 0 5 9.5c0 4.6 3 5.7 6 6A3.5 3.5 0 0 0 10 18v4"
    />
  </svg>
</template>

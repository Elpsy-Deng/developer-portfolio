<script setup lang="ts">
import AppIcon from './AppIcon.vue'
withDefaults(
  defineProps<{
    title: string
    description: string
    kind?: 'screenshot' | 'architecture'
    nodes?: string[]
  }>(),
  { kind: 'screenshot' },
)
</script>
<template>
  <figure
    class="media-placeholder"
    :class="{ 'architecture-placeholder': kind === 'architecture' }"
  >
    <div v-if="kind === 'architecture' && nodes?.length" class="architecture-nodes">
      <template v-for="(node, i) in nodes" :key="node"
        ><span v-if="i" class="node-arrow" aria-hidden="true">→</span
        ><span class="architecture-node"
          ><AppIcon :name="i === 0 ? 'layers' : 'code'" :size="23" />{{ node }}</span
        ></template
      >
    </div>
    <div v-else class="placeholder-window" aria-hidden="true">
      <div><i></i><i></i><i></i></div>
      <AppIcon :name="kind === 'architecture' ? 'layers' : 'image'" :size="38" />
    </div>
    <figcaption>
      <span class="placeholder-label">{{
        kind === 'architecture' && nodes?.length
          ? 'IMPLEMENTED STRUCTURE / CONCEPT DIAGRAM'
          : kind === 'architecture'
            ? 'ARCHITECTURE PLACEHOLDER'
            : 'SCREENSHOT PLACEHOLDER'
      }}</span>
      <h3>{{ title }}</h3>
      <p>{{ description }}</p>
    </figcaption>
  </figure>
</template>

<script setup lang="ts">
import type { ProjectStatus } from '../types/project'
defineProps<{ status: ProjectStatus }>()
</script>

<template>
  <span class="project-status" :class="{ 'status-progress': status === 'In Progress' }">
    <span aria-hidden="true"></span>{{ status }}
  </span>
</template>

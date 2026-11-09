<script setup lang="ts">
import type { ProjectSummary } from '../types/project'
import ProjectVisual from './ProjectVisual.vue'
import SkillBadge from './SkillBadge.vue'
import AppIcon from './AppIcon.vue'
import ProjectStatusBadge from './ProjectStatusBadge.vue'
defineProps<{ project: ProjectSummary; index?: number }>()
</script>
<template>
  <article class="project-card">
    <RouterLink
      :to="`/projects/${project.slug}`"
      class="project-cover"
      :aria-label="`查看 ${project.title} 專案介紹`"
      ><ProjectVisual :project="project"
    /></RouterLink>
    <div class="project-card-body">
      <ProjectStatusBadge :status="project.status" />
      <div class="project-meta">
        <span>{{ project.category }}</span
        ><span
          >{{ project.year }} <span class="mx-1">/</span>
          {{ String((index ?? 0) + 1).padStart(2, '0') }}</span
        >
      </div>
      <h3>
        <RouterLink :to="`/projects/${project.slug}`"
          ><span>{{ project.title }}</span
          ><AppIcon name="arrow" :size="23"
        /></RouterLink>
      </h3>
      <p>{{ project.summary }}</p>
      <span v-if="project.technologies.length" class="project-stack-note">{{
        project.technologyLabel
      }}</span>
      <div class="flex flex-wrap gap-2">
        <span v-if="!project.technologies.length" class="skill-badge">技術組合待補</span>
        <SkillBadge
          v-for="technology in project.technologies"
          :key="technology"
          :label="technology"
        />
      </div>
    </div>
  </article>
</template>

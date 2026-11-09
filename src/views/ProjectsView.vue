<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PaginationControls from '../components/PaginationControls.vue'
import { getProjects, getCategories } from '../api/projects'
import { useRemote } from '../composables/useRemote'
import ProjectLoadState from '../components/ProjectLoadState.vue'
import type { ProjectCategory } from '../types/project'
import ProjectCard from '../components/ProjectCard.vue'
import AppIcon from '../components/AppIcon.vue'
const route = useRoute(), router = useRouter()
const query = ref(typeof route.query.q === 'string' ? route.query.q : '')
const category = ref<ProjectCategory>(typeof route.query.category === 'string' ? route.query.category : '全部')
const page = ref(Number(route.query.page ?? 1)), pageSize = ref(Number(route.query.pageSize ?? 12))
watch([query, category, pageSize], () => { page.value = 1 }, { flush: 'sync' })
watch([query, category, page, pageSize], () => { void router.replace({ query: { ...(query.value ? { q: query.value } : {}), ...(category.value !== '全部' ? { category: category.value } : {}), page: String(page.value), pageSize: String(pageSize.value) } }) })
watch(() => route.query, value => {
  query.value = typeof value.q === 'string' ? value.q : ''
  category.value = typeof value.category === 'string' ? value.category : '全部'
  pageSize.value = Number(value.pageSize ?? 12); page.value = Number(value.page ?? 1)
})
const { data, loading, error, reload } = useRemote(
  () => JSON.stringify([query.value.trim(), category.value, page.value, pageSize.value]),
  async signal => {
    const [listing, categories] = await Promise.all([
      getProjects({ q: query.value, category: category.value, page: page.value, pageSize: pageSize.value }, signal), getCategories(signal),
    ])
    return { ...listing, categories }
  },
)
const filteredProjects = computed(() => data.value?.items ?? [])
const projectCategories = computed(() => data.value?.categories ?? [])
function resetFilters() {
  query.value = ''
  category.value = '全部'; page.value = 1; pageSize.value = 12
}
</script>

<template>
  <div class="container route-page">
    <div class="page-intro">
      <p class="eyebrow">THE PROJECT COLLECTION</p>
      <h1>想法的<span class="accent-text">實作紀錄.</span></h1>
      <p>整理全端、後端與工具專案，區分已完成範圍與規劃中的內容。</p>
      <span class="intro-aside" aria-hidden="true"
        >{{ data ? String(data.catalogTotal).padStart(2, '0') : '—' }}<small>PROJECTS</small></span
      >
    </div>
    <div class="project-controls">
      <div class="filter-tabs" role="group" aria-label="專案分類">
        <button
          v-for="item in ['全部', ...projectCategories] as const"
          :key="item"
          :class="{ selected: category === item }"
          :aria-pressed="category === item"
          @click="category = item"
        >
          {{ item }}<span v-if="item === '全部' && data">{{ data.catalogTotal }}</span>
        </button>
      </div>
      <div class="search-field">
        <AppIcon name="search" :size="18" /><label for="project-search" class="sr-only"
          >搜尋專案或技術</label
        ><input id="project-search" v-model="query" type="search" maxlength="120" placeholder="搜尋專案或技術…" />
      </div>
    </div>
    <ProjectLoadState :loading="loading" :error="error" @retry="reload" />
    <div v-if="data" class="results-info">
      <p role="status" aria-live="polite">
        {{ data.total }} 個專案<span v-if="query.trim()">
          · 搜尋「{{ query.trim() }}」</span
        >
      </p>
      <span>每一次實作，都是新的探索。</span>
    </div>
    <div v-if="data && filteredProjects.length" class="project-grid collection-grid">
      <ProjectCard
        v-for="(project, index) in filteredProjects"
        :key="project.slug"
        :project="project"
        :index="index"
      />
    </div>
    <div v-else-if="data" class="empty-state">
      <span class="empty-icon"><AppIcon name="search" :size="30" /></span>
      <h2>目前沒有符合的專案</h2>
      <p>試試其他關鍵字，或切換專案分類。</p>
      <button class="button button-dark" @click="resetFilters">
        清除篩選<AppIcon name="arrow" :size="18" />
      </button>
    </div>
    <PaginationControls v-if="data" :page="data.page" :page-size="data.pageSize" :total="data.total" :total-pages="data.totalPages" :disabled="loading" @page="page = $event" @size="pageSize = $event" />
    <p class="sample-caption">
      內容依各作品的原始碼與驗證文件整理。Cloud 展示完成與正式營運分開標示，
      測試數字保留原驗證條件；公開 Demo 與未取得的畫面仍待補。
    </p>
  </div>
</template>

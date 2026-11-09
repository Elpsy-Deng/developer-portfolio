<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getProject, getNextProject } from '../api/projects'
import { useRemote } from '../composables/useRemote'
import ProjectLoadState from '../components/ProjectLoadState.vue'
import AppIcon from '../components/AppIcon.vue'
import SkillBadge from '../components/SkillBadge.vue'
import PageSection from '../components/PageSection.vue'
import ProjectLinks from '../components/ProjectLinks.vue'
import ProjectVisual from '../components/ProjectVisual.vue'
import ProjectMediaPlaceholder from '../components/ProjectMediaPlaceholder.vue'
import ProjectScreenshot from '../components/ProjectScreenshot.vue'
import NotFoundView from './NotFoundView.vue'
import ProjectStatusBadge from '../components/ProjectStatusBadge.vue'
const route = useRoute()
const { data, loading, error, notFound, reload } = useRemote(
  () => String(route.params.slug),
  async signal => {
    const slug = String(route.params.slug)
    const project = await getProject(slug, signal)
    const next = await getNextProject(slug, signal)
    return { project, next }
  },
)
const project = computed(() => data.value?.project)
const nextProject = computed(() => data.value?.next)
const architectureFigures = computed(() => project.value?.screenshots.filter(item => item.title.startsWith('架構說明圖')) ?? [])
const productScreenshots = computed(() => project.value?.screenshots.filter(item => !item.title.startsWith('架構說明圖')) ?? [])
watch(
  project,
  (value) => {
    document.title = value ? `${value.title} — Devfolio` : '專案介紹 — Devfolio'
  },
  { immediate: true, flush: 'post' },
)
</script>

<template>
  <div v-if="project" class="container detail-page">
    <RouterLink to="/projects" class="back-link"
      ><span aria-hidden="true">←</span> 返回所有專案</RouterLink
    >
    <div class="detail-intro">
      <div>
        <p class="eyebrow">{{ project.category }} / {{ project.year }} / PROJECT RECORD</p>
        <ProjectStatusBadge :status="project.status" />
        <h1>{{ project.title }}<span>.</span></h1>
        <p class="detail-subtitle">{{ project.subtitle }}</p>
      </div>
      <ProjectLinks
        :github-url="project.githubUrl"
        :live-demo-url="project.liveDemoUrl"
        :local-demo-url="project.localDemoUrl"
      />
    </div>
    <div class="detail-cover">
      <ProjectVisual :project="project" /><span class="detail-cover-caption">{{
        project.coverImage ? '保存截圖 · 版本與條件見畫面說明' : '裝飾封面 · 非實際產品截圖或成果'
      }}</span>
    </div>
    <div class="detail-overview">
      <div>
        <p class="eyebrow">PROJECT OVERVIEW</p>
        <h2>關於這個專案</h2>
        <p>{{ project.description }}</p>
        <p class="project-progress-note">{{ project.statusNote }}</p>
      </div>
      <dl class="project-facts">
        <div>
          <dt>專案工作範圍</dt>
          <dd>{{ project.role }}</dd>
        </div>
        <div>
          <dt>開發時間</dt>
          <dd>{{ project.duration }}</dd>
        </div>
        <div>
          <dt>{{ project.technologyLabel }}</dt>
          <dd class="flex flex-wrap gap-2">
            <SkillBadge
              v-for="technology in project.technologies"
              :key="technology"
              :label="technology"
            />
            <span v-if="!project.technologies.length">尚未提供實際使用的技術。</span>
          </dd>
        </div>
      </dl>
    </div>
    <PageSection
      id="implementation-scope"
      eyebrow="01 / IMPLEMENTATION SCOPE"
      title="已完成的實作範圍"
      ><ul v-if="project.features.length" class="feature-list">
        <li v-for="(feature, index) in project.features" :key="feature">
          <span>0{{ index + 1 }}</span>
          <p>{{ feature }}</p>
          <AppIcon name="check" :size="20" />
        </li>
      </ul>
      <p v-else class="section-empty">
        {{ project.status }} · 尚未提供可驗證的已完成項目；待確認實際成果後補入。
      </p>
    </PageSection>
    <PageSection
      v-if="project.verification?.length"
      id="verification"
      eyebrow="VERIFICATION / OBSERVED RESULTS"
      title="可核對的驗證紀錄"
      description="以下為已執行的驗證，並附上環境與範圍。"
    >
      <ul class="scope-notes">
        <li v-for="item in project.verification" :key="item">{{ item }}</li>
      </ul>
    </PageSection>
    <PageSection
      v-if="project.evidenceLinks?.length"
      id="evidence"
      eyebrow="SOURCE / VERIFICATION DOCUMENTS"
      title="原始碼與驗證文件"
      :description="`資料查核：${project.evidenceCheckedAt}。以下文件保留各次驗證的版本、環境與限制；歷史結果不代表目前服務在線。`"
    >
      <ul class="scope-notes evidence-links">
        <li v-for="link in project.evidenceLinks" :key="link.url">
          <a :href="link.url" target="_blank" rel="noopener noreferrer">
            {{ link.label }}<AppIcon name="external" :size="14" />
          </a>
        </li>
      </ul>
    </PageSection>
    <PageSection
      v-if="project.limitations?.length"
      id="limitations"
      eyebrow="CURRENT LIMITATIONS"
      title="目前限制與尚未完成的範圍"
    >
      <ul class="scope-notes">
        <li v-for="item in project.limitations" :key="item">{{ item }}</li>
      </ul>
    </PageSection>
    <PageSection
      v-if="project.plannedGoals?.length"
      id="planned-scope"
      eyebrow="PROPOSED SCOPE / NOT IMPLEMENTED"
      title="規劃中的展示方向"
      description="以下為提案方向，尚未實作；完成後才會移入已完成範圍。"
    >
      <ul class="feature-list">
        <li v-for="(goal, index) in project.plannedGoals" :key="goal">
          <span>0{{ index + 1 }}</span>
          <p>{{ goal }}</p>
          <AppIcon name="arrow" :size="20" />
        </li>
      </ul>
    </PageSection>
    <PageSection
      eyebrow="02 / SCREENSHOTS"
      title="介面與畫面"
      description="畫面依說明標示來源與版本；架構說明圖不代表來源應用的操作截圖，缺少可核實畫面時保留待補提示。"
      ><div class="screenshot-grid">
        <ProjectScreenshot
          v-for="screenshot in productScreenshots"
          :key="screenshot.title"
          :screenshot="screenshot"
        /><p v-if="!productScreenshots.length" class="section-empty">尚未提供可核實的來源應用操作截圖。</p></div
    ></PageSection>
    <PageSection
      eyebrow="03 / ARCHITECTURE"
      title="從結構理解實作"
      :description="
        architectureFigures.length
          ? '依固定來源程式與證據整理；點選圖片可查看原尺寸。'
          : project.architecture.length
          ? '以概念節點呈現已實作的路徑與分層。'
          : '架構尚未提供，不預設技術分層、服務或資料庫。'
      "
      ><div v-if="architectureFigures.length" class="screenshot-grid">
        <ProjectScreenshot v-for="figure in architectureFigures" :key="figure.title" :screenshot="figure" />
      </div>
      <ProjectMediaPlaceholder v-else
        kind="architecture"
        :title="project.architecture.length ? '已實作的專案結構' : '專案架構待補'"
        :description="project.architectureDescription ?? '待確認實際實作後，再補入對應架構圖。'"
        :nodes="project.architecture"
    /></PageSection>
    <PageSection eyebrow="04 / ENGINEERING NOTES" title="挑戰與技術選擇"
      ><div v-if="project.challenges.length" class="challenge-grid">
        <article
          v-for="(challenge, index) in project.challenges"
          :key="challenge.title"
          class="challenge-card"
        >
          <span class="skill-number">0{{ index + 1 }}</span>
          <h3>{{ challenge.title }}</h3>
          <p>{{ challenge.description }}</p>
        </article>
      </div>
      <p v-else class="section-empty">尚未提供實際遇到的技術挑戰與解法，待實作紀錄確認後補入。</p>
    </PageSection>
    <RouterLink v-if="nextProject" :to="`/projects/${nextProject.slug}`" class="next-project"
      ><div>
        <p class="eyebrow">KEEP EXPLORING / 下一個專案</p>
        <h2>{{ nextProject.title }}<span>.</span></h2>
      </div>
      <span class="next-arrow"><AppIcon name="arrow" :size="30" /></span
    ></RouterLink>
  </div>
  <NotFoundView v-else-if="notFound" kind="project" />
  <div v-else class="container route-page">
    <RouterLink to="/projects" class="back-link">← 返回所有專案</RouterLink>
    <ProjectLoadState :loading="loading" :error="error" @retry="reload" />
  </div>
</template>

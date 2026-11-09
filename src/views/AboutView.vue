<script setup lang="ts">
import { profile } from '../data/profile'
import AppIcon from '../components/AppIcon.vue'
import PageSection from '../components/PageSection.vue'
import SkillBadge from '../components/SkillBadge.vue'
const principles = [
  {
    title: '先釐清需求與 API 邊界',
    text: '關注輸入、輸出、錯誤處理與資料流程，讓介面契約與需求保持一致。',
  },
  {
    title: '讓資料與系統結構清楚',
    text: '在意職責分層、資料模型與可維護性，持續學習如何說明技術選擇與取捨。',
  },
  {
    title: '用可驗證成果說明工程能力',
    text: '如實記錄已完成項目、實作限制與開發進度；規劃中的功能保留明確的待補狀態。',
  },
]
</script>

<template>
  <div class="container route-page about-page">
    <div class="page-intro">
      <p class="eyebrow">A LITTLE ABOUT ME</p>
      <h1>從需求出發，<span class="accent-text">理解系統.</span></h1>
      <p>Java Full Stack / Backend Engineer 求職作品集。</p>
    </div>
    <div class="about-profile">
      <div class="profile-art" aria-hidden="true">
        <span class="profile-art-code">&lt; / &gt;</span><span class="profile-art-star">✳</span>
        <div>
          <span>THINK. BUILD. REFINE.</span><b>{{ profile.title }}</b>
        </div>
      </div>
      <div class="profile-copy">
        <p class="eyebrow">JAVA / FULL STACK / BACKEND</p>
        <h2>以工程思考，串起完整流程。</h2>
        <p>{{ profile.intro }}</p>
        <p>
          技能內容依後端、前端、資料庫與工程實務分類。專案介紹會區分已實作成果與規劃中的內容，後續可依真實經歷補入
          API、資料模型、權限設計與技術決策的紀錄。
        </p>
        <span class="profile-location">
          <template v-if="profile.location"><AppIcon name="pin" :size="17" />{{ profile.location }}<span>·</span></template>
          {{ profile.title }}
        </span>
        <p class="profile-sample">個人姓名、聯絡方式與真實經歷暫不公開。以下技術來自作品程式碼，並非任職或熟練程度聲明。</p>
      </div>
    </div>
    <PageSection eyebrow="01 / SKILLS & TOOLS" title="Java 全端與後端工具箱"
      ><div class="skills-grid">
        <article
          v-for="(group, index) in profile.skillGroups"
          :key="group.title"
          class="skill-group"
        >
          <span class="skill-number">0{{ index + 1 }}</span>
          <h3>{{ group.title }}</h3>
          <p>{{ group.description }}</p>
          <div class="flex flex-wrap gap-2">
            <SkillBadge v-for="skill in group.skills" :key="skill" :label="skill" />
          </div>
        </article>
      </div>
      <p class="sample-caption">技能分類呈現求職方向；實際使用經歷與熟練程度待補。</p>
    </PageSection>
    <PageSection eyebrow="02 / HOW I WORK" title="在意的開發原則"
      ><div class="principles">
        <article v-for="(principle, index) in principles" :key="principle.title">
          <span>0{{ index + 1 }}</span>
          <h3>{{ principle.title }}</h3>
          <p>{{ principle.text }}</p>
        </article>
      </div></PageSection
    >
    <section class="about-strip">
      <span class="strip-star" aria-hidden="true">✳</span>
      <div>
        <p class="eyebrow">SEE THE THINKING IN ACTION</p>
        <h2>透過作品，認識我的思考。</h2>
        <p>探索專案的內容、技術組合與實作挑戰。</p>
      </div>
      <RouterLink to="/projects" class="button button-dark"
        >瀏覽作品<AppIcon name="arrow" :size="18"
      /></RouterLink>
    </section>
  </div>
</template>

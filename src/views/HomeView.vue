<script setup lang="ts">
import { computed } from 'vue'
import { getProjects } from '../api/projects'
import { useRemote } from '../composables/useRemote'
import ProjectLoadState from '../components/ProjectLoadState.vue'
import { profile } from '../data/profile'
import PageSection from '../components/PageSection.vue'
import ProjectCard from '../components/ProjectCard.vue'
import SkillBadge from '../components/SkillBadge.vue'
import AppIcon from '../components/AppIcon.vue'
const { data, loading, error, reload } = useRemote(() => 'featured', signal => getProjects({ featured: true }, signal))
const featuredProjects = computed(() => data.value?.items ?? [])
</script>

<template>
  <section class="hero">
    <div class="container hero-grid">
      <div class="hero-copy">
        <p class="hero-kicker"><span></span> JAVA FULL STACK / BACKEND ENGINEER</p>
        <h1>
          把需求，<br />寫成<span class="hero-highlight"
            >可靠的系統<span class="highlight-dot">.</span></span
          >
        </h1>
        <p class="hero-description">
          以 Java 後端與全端開發為求職方向。<br />關注 API 設計、資料流程與可維護的系統結構。
        </p>
        <div class="hero-actions">
          <RouterLink to="/projects" class="button button-lime"
            >探索我的作品<AppIcon name="arrow" :size="18" /></RouterLink
          ><RouterLink to="/about" class="hero-about"
            >更多關於我<AppIcon name="external" :size="16"
          /></RouterLink>
        </div>
        <div class="hero-location">
          <AppIcon name="pin" :size="14" /> {{ profile.location }}<span>·</span
          ><span>持續學習，持續創作</span>
        </div>
      </div>
      <div class="hero-art" aria-hidden="true">
        <div class="art-orbit orbit-one"></div>
        <div class="art-orbit orbit-two"></div>
        <div class="art-code">
          <div class="art-code-top">
            <span><i></i><i></i><i></i></span><small>Developer.java</small
            ><AppIcon name="code" :size="15" />
          </div>
          <div class="art-code-body">
            <div>
              <span class="line-number">01</span><span class="code-purple">class</span> Developer {
            </div>
            <div>
              <span class="line-number">02</span>&nbsp; String focus =
              <span class="code-lime">"reliable systems"</span>;
            </div>
            <div>
              <span class="line-number">03</span>&nbsp; String[] stack = {<span class="code-lime"
                >"Java"</span
              >, <span class="code-lime">"Vue"</span>};
            </div>
            <div>
              <span class="line-number">04</span>&nbsp; String mindset =
              <span class="code-lime">"always learning"</span>;
            </div>
            <div><span class="line-number">05</span>}</div>
            <div class="code-comment">
              <span class="line-number">06</span>// A little better, every day.
            </div>
          </div>
        </div>
        <div class="art-sticker">
          <AppIcon name="code" :size="30" /><span>Made with<br /><b>curiosity.</b></span
          ><span class="sticker-star">✳</span>
        </div>
        <div class="art-note">
          <span class="note-check"><AppIcon name="check" :size="17" /></span>
          <div><b>Requirements → Systems</b><small>THOUGHTFULLY BUILT</small></div>
        </div>
        <span class="art-spark spark-one">+</span><span class="art-spark spark-two">+</span>
      </div>
    </div>
    <div class="container hero-bottom">
      <span>CODE WITH PURPOSE. DESIGN WITH CARE.</span
      ><a href="#selected-work">往下探索 <span>↓</span></a>
    </div>
  </section>

  <div class="container">
    <PageSection
      id="selected-work"
      eyebrow="01 / SELECTED WORK"
      title="精選作品"
      description="從已完成範圍到待補內容，清楚記錄每個專案的進度。"
    >
      <template #action
        ><RouterLink to="/projects" class="text-link"
          >所有專案 <span v-if="data" class="count-pill">{{ data.catalogTotal }}</span
          ><AppIcon name="arrow" :size="18" /></RouterLink
      ></template>
      <ProjectLoadState :loading="loading" :error="error" @retry="reload" />
      <p v-if="data && !featuredProjects.length" class="section-empty">目前沒有精選作品。</p>
      <div v-if="data" class="project-grid featured-grid">
        <ProjectCard
          v-for="(project, index) in featuredProjects"
          :key="project.slug"
          :project="project"
          :index="index"
        />
      </div>
      <p class="sample-caption">
        四個主要作品各有實作與驗證文件。Atlas 使用歷史真實 RAG 截圖；
        其他封面為裝飾示意，Cloud 展示完成不代表正式營運。
      </p>
    </PageSection>
    <PageSection
      eyebrow="02 / MY TOOLBOX"
      title="Java 全端與後端工具箱"
      description="以後端、前端、資料庫與工程實務整理技能方向。"
    >
      <div class="skills-grid">
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
      <p class="sample-caption">
        技能依求職方向整理；熟練程度與實際使用經歷可再補入，並不代表每個展示專案都使用這些技術。
      </p>
    </PageSection>
    <section class="about-strip">
      <span class="strip-star" aria-hidden="true">✳</span>
      <div>
        <p class="eyebrow">BEHIND THE CODE</p>
        <h2>好的作品，始於好奇心。</h2>
        <p>認識我的開發方式、學習方向，以及對產品的思考。</p>
      </div>
      <RouterLink to="/about" class="button button-dark"
        >認識我<AppIcon name="arrow" :size="18"
      /></RouterLink>
    </section>
  </div>
</template>

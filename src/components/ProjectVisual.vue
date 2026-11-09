<script setup lang="ts">
import type { ProjectSummary } from '../types/project'
defineProps<{ project: ProjectSummary }>()
</script>

<template>
  <div
    v-if="project.coverImage"
    class="project-visual visual-screenshot"
    :class="`visual-${project.theme}`"
  >
    <span class="visual-label">{{ project.category }} / ACTUAL SCREENSHOT</span>
    <div class="mini-browser">
      <div class="browser-bar">
        <i></i><i></i><i></i><span>{{ project.slug }} / 保存畫面</span>
      </div>
      <img :src="project.coverImage.src" :alt="project.coverImage.alt" class="cover-screenshot" />
    </div>
    <span class="visual-corner">{{ project.title }}<span>↗</span></span>
  </div>
  <div v-else class="project-visual" :class="`visual-${project.theme}`" aria-hidden="true">
    <span class="visual-label">{{ project.category }} / DECORATIVE PLACEHOLDER</span>
    <div class="mini-browser">
      <div class="browser-bar">
        <i></i><i></i><i></i><span>{{ project.slug }} / mock cover</span>
      </div>
      <div v-if="project.theme === 'forest'" class="camp-preview">
        <div class="mini-nav">
          <b>{{ project.title }}<span>✳</span></b
          ><span>PROJECT PLACEHOLDER</span>
        </div>
        <div class="camp-scene">
          <span class="sun"></span><span class="mountain mountain-back"></span
          ><span class="mountain mountain-front"></span>
          <div class="scene-copy">
            A project<br />taking shape.<span>Decorative cover / See project details</span>
          </div>
        </div>
        <div class="camp-search">
          <span>Scope<br /><b>See details</b></span
          ><span>Evidence<br /><b>See documents</b></span
          ><i>↗</i>
        </div>
      </div>
      <div v-else-if="project.theme === 'blue'" class="metric-preview">
        <aside><b>ai.</b><i></i><i></i><i></i><i></i></aside>
        <div class="metric-content">
          <div class="mini-nav">
            <b>{{ project.title }}</b
            ><span>MOCK COVER</span>
          </div>
          <div class="metric-stats">
            <div>
              <span>Status</span><b>{{ project.status }}</b
              ><small>Progress to be confirmed</small>
            </div>
            <div><span>Verified scope</span><b>—</b><small>Details pending</small></div>
          </div>
          <div class="mini-chart">
            <span>Decorative graphic / not project data</span>
            <div class="chart-bars">
              <i
                v-for="(height, i) in [25, 39, 34, 49, 44, 61, 55, 71, 68, 83, 77, 96]"
                :key="i"
                :style="{ height: `${height}%` }"
              ></i>
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="project.theme === 'peach'" class="folio-preview">
        <div class="mini-nav"><b>devfolio.</b><span>WORK &nbsp; ABOUT</span></div>
        <span class="mini-eyebrow">JAVA. FULL STACK. BACKEND.</span
        ><strong>Engineering<br /><em>in progress.</em></strong>
        <div class="folio-blocks">
          <div><span>01 / PROJECT SCOPE</span></div>
          <div><span>02 / TECHNICAL NOTES</span></div>
        </div>
      </div>
      <div v-else class="snippet-preview">
        <div class="mini-nav">
          <b>⌘ {{ project.category }}</b
          ><span>PROJECT PLACEHOLDER</span>
        </div>
        <div class="code-layout">
          <aside>Overview<br /><br />Scope<br /><br />Stack<br /><br />Notes</aside>
          <div>
            <span class="code-tag">CONTENT PLACEHOLDER</span>
            <pre><code><span>// Project record</span>
status: <em>{{ project.status }}</em>
scope: <em>see project details</em>
evidence: <em>see documents</em>

<span>// Decorative cover</span></code></pre>
          </div>
        </div>
      </div>
    </div>
    <span class="visual-corner">{{ project.title }}<span>↗</span></span>
  </div>
</template>

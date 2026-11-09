<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSession, AdminApiError } from '../api/admin'
import { getAudit, type Audit } from '../api/adminProjects'
const route = useRoute(), router = useRouter(), rows = ref<Audit[]>([]), busy = ref(false), error = ref('')
let id = 0
async function load() {
  const current = ++id; busy.value = true; error.value = ''; rows.value = []
  try {
    const identity = await getSession()
    if (current !== id) return
    if (!identity.authenticated) { await router.replace('/admin/login'); return }
    const result = await getAudit(String(route.params.slug))
    if (current === id) rows.value = result
  } catch (e) {
    if (current !== id) return
    if (e instanceof AdminApiError && e.status === 401) await router.replace('/admin/login')
    else error.value = e instanceof Error ? e.message : '稽核讀取失敗'
  } finally { if (current === id) busy.value = false }
}
const action = { CREATE: '新增', UPDATE: '更新', DELETE: '刪除' }
watch(() => route.fullPath, load, { immediate: true })
</script>
<template>
  <section class="container route-page admin-catalog"><div class="page-intro"><p class="eyebrow">PORTFOLIO / AUDIT</p><h1>作品稽核紀錄</h1><p>{{ route.params.slug }} · 最近 50 筆；作品刪除後仍可核對。</p></div>
    <RouterLink class="button button-outline" to="/admin/projects">返回作品列表</RouterLink><p v-if="busy" role="status">正在讀取稽核…</p><p v-if="error" role="alert">{{ error }}</p><button v-if="error" class="button button-outline" @click="load">重新載入</button>
    <p v-if="!busy && !error && !rows.length">尚無管理操作紀錄。</p>
    <ol class="admin-project-list"><li v-for="row in rows" :key="row.id" class="admin-audit-row"><div><h2>{{ action[row.action] }}</h2><p>操作人：{{ row.actor }} · {{ row.createdAt }}</p><p>{{ row.before ? `版本 ${row.before.version}` : '新作品' }} → {{ row.after ? `版本 ${row.after.version}` : '已刪除' }}</p>
      <details><summary>核對操作前後內容</summary><div class="admin-audit-comparison"><div v-for="(snapshot, index) in [row.before, row.after]" :key="index"><h3>{{ index === 0 ? '操作前' : '操作後' }}</h3><template v-if="snapshot"><p>{{ snapshot.project.title }} · {{ snapshot.published ? '已發布' : '草稿' }} · 排序 {{ snapshot.sortOrder }}</p><p>{{ snapshot.project.summary }}</p><p>{{ snapshot.project.description }}</p><p>技術：{{ snapshot.project.technologies.join('、') }}</p><ul><li v-for="feature in snapshot.project.features" :key="feature">{{ feature }}</li></ul></template><p v-else>沒有作品內容</p></div></div></details>
    </div></li></ol>
  </section>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getSession, AdminApiError, type AdminSession } from '../api/admin'
import { listManaged, getOptions, type ManagedRow, type ManagedListing } from '../api/adminProjects'
import PaginationControls from '../components/PaginationControls.vue'
const router = useRouter()
const session = ref<AdminSession | null>(null)
const rows = ref<ManagedRow[]>([])
const busy = ref(false)
const error = ref('')
const q = ref(''), category = ref(''), published = ref(''), page = ref(1), pageSize = ref(12)
const categories = ref<string[]>([]), listing = ref<ManagedListing | null>(null)
let epoch = 0, controller: AbortController | undefined
watch([q, category, published, pageSize], () => { page.value = 1 }, { flush: 'sync' })
watch([q, category, published, page, pageSize], load)
onUnmounted(() => { epoch++; controller?.abort() })
async function load() {
  const current = ++epoch; controller?.abort(); controller = new AbortController(); const signal = controller.signal
  busy.value = true; error.value = ''; rows.value = []; session.value = null; listing.value = null
  try {
    const identity = await getSession()
    if (current !== epoch) return
    if (!identity.authenticated) { await router.replace('/admin/login'); return }
    const [result, options] = await Promise.all([listManaged({ q: q.value, category: category.value, published: published.value, page: page.value, pageSize: pageSize.value }, signal), getOptions(signal)])
    if (current !== epoch || signal.aborted) return
    session.value = identity; rows.value = result.items; listing.value = result; categories.value = options.categories
  } catch (e) {
    if (current !== epoch || signal.aborted) return
    if (e instanceof AdminApiError && e.status === 401) await router.replace('/admin/login')
    else error.value = e instanceof Error ? e.message : '作品資料讀取失敗'
  } finally { if (current === epoch && !signal.aborted) busy.value = false }
}
onMounted(load)
</script>
<template>
  <section class="container route-page admin-catalog">
    <div class="page-intro"><p class="eyebrow">PORTFOLIO / CATALOG</p><h1>作品管理</h1><p>草稿只在管理區顯示，發布後訪客才能閱讀。</p></div>
    <div class="admin-actions"><RouterLink class="button button-outline" to="/admin">管理概覽</RouterLink><RouterLink v-if="session?.role === 'ADMIN'" class="button button-dark" to="/admin/projects/new">新增作品</RouterLink></div>
    <div class="admin-panel admin-form">
      <label for="managed-query">搜尋管理作品</label><input id="managed-query" v-model="q" type="search" maxlength="120" />
      <label for="managed-category">作品分類</label><select id="managed-category" v-model="category"><option value="">全部</option><option v-for="item in categories" :key="item">{{ item }}</option></select>
      <label for="managed-published">發布範圍</label><select id="managed-published" v-model="published"><option value="">全部</option><option value="true">已發布</option><option value="false">草稿</option></select>
    </div>
    <p v-if="busy" role="status">正在讀取作品…</p><p v-if="error" role="alert">{{ error }}</p><button v-if="error" class="button button-outline" @click="load">重新載入</button>
    <p v-if="session?.role === 'VIEWER'" class="admin-note">唯讀角色可以核對內容與稽核，不能修改作品。</p>
    <p v-if="session && !rows.length">本頁沒有符合的作品。</p>
    <ul class="admin-project-list"><li v-for="row in rows" :key="row.slug" :data-slug="row.slug">
      <div><h2>{{ row.title }}</h2><p>{{ row.category }} · {{ row.published ? '已發布' : '草稿' }} · 版本 {{ row.version }} · 排序 {{ row.sortOrder }}</p><small>{{ row.slug }}</small></div>
      <div class="admin-actions"><RouterLink class="button button-outline" :to="`/admin/projects/${row.slug}`">{{ session?.role === 'ADMIN' ? '編輯' : '檢視' }}</RouterLink><RouterLink class="button button-outline" :to="`/admin/projects/${row.slug}/audit`">稽核紀錄</RouterLink></div>
    </li></ul>
    <PaginationControls v-if="listing" :page="listing.page" :page-size="listing.pageSize" :total="listing.total" :total-pages="listing.totalPages" :disabled="busy" @page="page = $event" @size="pageSize = $event" />
  </section>
</template>

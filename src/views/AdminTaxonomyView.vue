<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getSession, AdminApiError, type AdminSession } from '../api/admin'
import { listTerms, saveTerm, deleteTerm, termAudit, type Kind, type Term, type TermAudit } from '../api/taxonomy'
const router = useRouter()
const session = ref<AdminSession | null>(null), rows = ref<Term[]>([]), audit = ref<TermAudit[]>([])
const kind = ref<Kind>('categories'), selected = ref<Term | null>(null), label = ref(''), order = ref(0)
const busy = ref(false), error = ref(''), notice = ref(''), conflict = ref(false), confirmDelete = ref(false)
let epoch = 0
onUnmounted(() => { epoch++ })
function edit(row: Term | null) { selected.value = row ? { ...row } : null; label.value = row?.label ?? ''; order.value = row?.sortOrder ?? 0; confirmDelete.value = false; conflict.value = false; error.value = '' }
async function fail(e: unknown) {
  if (e instanceof AdminApiError && e.status === 401) { rows.value = []; audit.value = []; session.value = null; await router.replace('/admin/login'); return }
  error.value = e instanceof Error ? e.message : '分類管理暫時無法使用'
  conflict.value = e instanceof AdminApiError && e.status === 409
  if (e instanceof AdminApiError && e.status === 0) error.value += '；結果未確認，請先核對，不要直接重送。'
}
async function load() {
  const current = ++epoch
  busy.value = true; error.value = ''; rows.value = []; audit.value = []; session.value = null
  try {
    const identity = await getSession()
    if (current !== epoch) return
    if (!identity.authenticated) { await router.replace('/admin/login'); return }
    const [terms, history] = await Promise.all([listTerms(kind.value), termAudit()])
    if (current !== epoch) return
    session.value = identity; rows.value = terms; audit.value = history
  } catch (e) { if (current === epoch) await fail(e) } finally { if (current === epoch) busy.value = false }
}
async function switchKind(value: Kind) { kind.value = value; edit(null); notice.value = ''; await load() }
async function latest() { const id = selected.value?.id; await load(); const row = rows.value.find(r => r.id === id); if (row) edit(row); else if (session.value) edit(null) }
async function saved(message: string) { edit(null); await load(); if (session.value) notice.value = message; else if (error.value) error.value = `${message} 清單讀取失敗：${error.value}` }
async function save() {
  if (!session.value || busy.value || session.value.role !== 'ADMIN') return
  busy.value = true; error.value = ''; notice.value = ''; conflict.value = false
  try { await saveTerm(kind.value, label.value, order.value, selected.value, session.value); await saved('分類或技術已保存；引用作品改名時會同步更新版本與稽核。') }
  catch (e) { await fail(e) } finally { busy.value = false }
}
async function remove() {
  if (!selected.value || !session.value || busy.value || !confirmDelete.value) return
  busy.value = true; error.value = ''; notice.value = ''; conflict.value = false
  try { await deleteTerm(kind.value, selected.value, session.value); await saved('項目已刪除，稽核仍保留。') }
  catch (e) { await fail(e) } finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <section class="container route-page admin-page">
    <div class="page-intro"><p class="eyebrow">PORTFOLIO / TAXONOMY</p><h1>分類與技術管理</h1><p>改名同步引用作品的版本與稽核；引用中的項目不能刪除。</p></div>
    <div class="admin-actions"><RouterLink class="button button-outline" to="/admin">管理概覽</RouterLink><button class="button button-outline" :disabled="busy" @click="switchKind('categories')">分類</button><button class="button button-outline" :disabled="busy" @click="switchKind('technologies')">技術</button></div>
    <p v-if="busy" role="status">正在讀取分類與技術…</p><p v-if="error" role="alert">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p><button v-if="error && !conflict" class="button button-outline" :disabled="busy" @click="load">重新載入項目</button>
    <template v-if="session">
      <p v-if="session.role === 'VIEWER'">唯讀檢視者可核對分類、技術及稽核，不能修改。</p>
      <ul class="admin-project-list"><li v-for="row in rows" :key="row.id" :data-term="row.id"><div><h2>{{ row.label }}</h2><p>版本 {{ row.version }} · 引用 {{ row.references }} 個作品<span v-if="row.sortOrder !== null"> · 排序 {{ row.sortOrder }}</span></p></div><button v-if="session.role === 'ADMIN'" class="button button-outline" :disabled="busy" @click="edit(row)">編輯 {{ row.label }}</button></li></ul>
      <form v-if="session.role === 'ADMIN'" class="admin-panel admin-form" @submit.prevent="save">
        <h2>{{ selected ? '編輯項目' : '新增項目' }}</h2><p v-if="selected">核對版本 {{ selected.version }}</p>
        <label for="term-label">項目名稱</label><input id="term-label" v-model="label" :maxlength="kind === 'categories' ? 40 : 80" required :disabled="busy" />
        <template v-if="kind === 'categories'"><label for="term-order">分類排序</label><input id="term-order" v-model.number="order" type="number" min="0" max="2147483647" required :disabled="busy" /></template>
        <button type="submit" class="button button-dark" :disabled="busy">儲存項目</button><button v-if="selected" type="button" class="button button-outline" :disabled="busy" @click="edit(null)">改為新增項目</button>
        <button v-if="conflict" type="button" class="button button-outline" :disabled="busy" @click="latest">載入最新項目版本</button>
        <template v-if="selected"><label><input v-model="confirmDelete" type="checkbox" :disabled="busy" />確認刪除此項目</label><button type="button" class="button button-outline" :disabled="busy || !confirmDelete" @click="remove">刪除項目</button></template>
      </form>
      <div class="admin-panel"><h2>最近分類與技術稽核</h2><p v-if="!audit.length">尚無稽核</p><ul><li v-for="entry in audit" :key="entry.id">{{ entry.action }} · {{ entry.kind }} · {{ entry.before?.label ?? '新項目' }} → {{ entry.after?.label ?? '已刪除' }} · {{ entry.actor }} · {{ entry.createdAt }}</li></ul></div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminTextList from '../components/AdminTextList.vue'
import AdminPairList, { type Pair } from '../components/AdminPairList.vue'
import { getSession, AdminApiError, type AdminSession } from '../api/admin'
import { getManaged, getOptions, saveManaged, deleteManaged, type ManagedProject, type ProjectContent } from '../api/adminProjects'
const route = useRoute(), router = useRouter()
const session = ref<AdminSession | null>(null), form = ref<ProjectContent | null>(null), managed = ref<ManagedProject | null>(null)
const categories = ref<string[]>([]), technologies = ref<string[]>([]), challenges = ref<Pair[]>([]), evidence = ref<Pair[]>([])
const busy = ref(false), error = ref(''), success = ref(''), conflict = ref(false), confirmDelete = ref(false)
const published = ref(false), sortOrder = ref<number | null>(null)
let loadId = 0
const creating = () => route.params.slug === undefined
const lists = [{ key: 'technologies', label: '技術標籤' }, { key: 'features', label: '已完成功能' }, { key: 'plannedGoals', label: '後續目標' }, { key: 'verification', label: '驗證成果' }, { key: 'limitations', label: '限制' }, { key: 'architecture', label: '架構節點' }] as const
const shortFields = [{ key: 'title', label: '作品名稱', max: 160, required: true }, { key: 'subtitle', label: '副標題', max: 300 }, { key: 'statusNote', label: '狀態說明', max: 1000 }, { key: 'year', label: '年份', max: 40 }, { key: 'role', label: '負責角色', max: 160 }, { key: 'duration', label: '開發階段／期間', max: 160 }, { key: 'technologyLabel', label: '技術說明', max: 160 }] as const
function content(p: ManagedProject['project']): ProjectContent {
  const { coverImage: _cover, screenshots: _screens, localDemoUrl: _local, ...core } = p
  return { ...core, plannedGoals: core.plannedGoals ?? [], verification: core.verification ?? [], limitations: core.limitations ?? [], architectureDescription: core.architectureDescription ?? '', evidenceLinks: core.evidenceLinks ?? [], evidenceCheckedAt: core.evidenceCheckedAt ?? '' }
}
function apply(value: ManagedProject) {
  managed.value = value; form.value = content(value.project); published.value = value.published; sortOrder.value = value.sortOrder
  challenges.value = value.project.challenges.map(c => ({ first: c.title, second: c.description }))
  evidence.value = (value.project.evidenceLinks ?? []).map(c => ({ first: c.label, second: c.url }))
}
async function failure(e: unknown) {
  if (e instanceof AdminApiError && e.status === 401) { await router.replace('/admin/login'); return }
  conflict.value = e instanceof AdminApiError && e.status === 409
  error.value = e instanceof AdminApiError && e.status === 0 ? '操作結果尚未確認，請重新載入作品核對後再操作' : e instanceof Error ? e.message : '操作失敗'
}
async function load() {
  const id = ++loadId; busy.value = true; error.value = ''; success.value = ''; conflict.value = false; confirmDelete.value = false; form.value = null; session.value = null; managed.value = null
  try {
    const identity = await getSession()
    if (id !== loadId) return
    if (!identity.authenticated) { await router.replace('/admin/login'); return }
    if (creating() && identity.role !== 'ADMIN') { error.value = '你沒有新增作品的權限'; return }
    const [options, value] = await Promise.all([getOptions(), creating() ? Promise.resolve(null) : getManaged(String(route.params.slug))])
    if (id !== loadId) return
    session.value = identity; categories.value = options.categories; technologies.value = options.technologies
    if (value) apply(value)
    else {
      published.value = false; sortOrder.value = null; challenges.value = []; evidence.value = []
      form.value = { slug: '', title: '', subtitle: '', category: options.categories[0] as ProjectContent['category'], status: 'In Progress', statusNote: '', summary: '', description: '', year: String(new Date().getFullYear()), role: '', duration: '', featured: false, theme: 'forest', technologies: [], technologyLabel: '', features: [], plannedGoals: [], verification: [], limitations: [], challenges: [], architecture: [], architectureDescription: '', githubUrl: null, liveDemoUrl: null, evidenceLinks: [], evidenceCheckedAt: '' }
    }
  } catch (e) { if (id === loadId) await failure(e) }
  finally { if (id === loadId) busy.value = false }
}
async function save() {
  if (!form.value || !session.value || session.value.role !== 'ADMIN' || busy.value) return
  busy.value = true; error.value = ''; success.value = ''; conflict.value = false
  const isNew = creating(), stage = loadId
  try {
    const core: ProjectContent = { ...form.value, ...Object.fromEntries(lists.map(list => [list.key, (form.value![list.key] ?? []).map(s => s.trim()).filter(Boolean)])), challenges: challenges.value.map(c => ({ title: c.first, description: c.second })), evidenceLinks: evidence.value.map(c => ({ label: c.first, url: c.second })), evidenceCheckedAt: form.value.evidenceCheckedAt || undefined, githubUrl: form.value.githubUrl || null, liveDemoUrl: form.value.liveDemoUrl || null }
    const result = await saveManaged({ content: core, expectedVersion: managed.value?.version ?? null, published: published.value, sortOrder: sortOrder.value === null || String(sortOrder.value) === '' ? null : Number(sortOrder.value) }, session.value, isNew)
    if (stage !== loadId) return
    if (isNew) await router.replace(`/admin/projects/${result.project.slug}`)
    else { apply(result); success.value = '作品已儲存'; confirmDelete.value = false }
  } catch (e) { await failure(e) }
  finally { busy.value = false }
}
async function remove() {
  if (!managed.value || !session.value || busy.value || !confirmDelete.value) return
  busy.value = true; error.value = ''; conflict.value = false
  try { const slug = managed.value.project.slug; await deleteManaged(slug, managed.value.version, session.value); await router.replace(`/admin/projects/${slug}/audit`) }
  catch (e) { await failure(e) }
  finally { busy.value = false }
}
watch(() => route.fullPath, load, { immediate: true })
</script>
<template>
  <section class="container route-page admin-editor">
    <div class="page-intro"><p class="eyebrow">PORTFOLIO / EDIT</p><h1>{{ creating() ? '新增作品' : session?.role === 'VIEWER' ? '檢視作品' : '編輯作品' }}</h1><p>保存核心內容與來源；圖片另於圖片管理編輯。草稿不會出現在公開頁。</p></div>
    <div class="admin-actions"><RouterLink class="button button-outline" to="/admin/projects">返回作品列表</RouterLink><RouterLink v-if="managed" class="button button-outline" :to="`/admin/projects/${managed.project.slug}/media`">圖片管理</RouterLink><RouterLink v-if="managed" class="button button-outline" :to="`/admin/projects/${managed.project.slug}/audit`">稽核紀錄</RouterLink></div>
    <p v-if="busy && !form" role="status">正在讀取作品…</p><p v-if="error" role="alert">{{ error }}</p><p v-if="success" role="status">{{ success }}</p>
    <div v-if="conflict" class="admin-conflict"><p>目前輸入已保留。載入最新資料會取代表單，請先核對或複製尚未保存的內容。</p><button class="button button-outline" :disabled="busy" @click="load">載入最新版本</button></div>
    <button v-if="error && !form" class="button button-outline" :disabled="busy" @click="load">重新載入</button>
    <form v-if="form" class="admin-panel admin-edit-form" @submit.prevent="save">
      <p v-if="managed" data-testid="project-version">目前版本 {{ managed.version }}</p><p v-if="session?.role === 'VIEWER'">唯讀身份不能儲存、發布或刪除。</p>
      <fieldset :disabled="busy || session?.role !== 'ADMIN'">
        <legend>基本資料</legend>
        <div class="admin-field"><label for="project-slug">網址代號（建立後固定）</label><input id="project-slug" v-model="form.slug" :readonly="!creating()" required pattern="[a-z0-9]+(-[a-z0-9]+)*" maxlength="80" /></div>
        <div v-for="field in shortFields" :key="field.key" class="admin-field"><label :for="`project-${field.key}`">{{ field.label }}</label><input :id="`project-${field.key}`" v-model="form[field.key]" :required="field.key === 'title'" :maxlength="field.max" /></div>
        <div class="admin-field"><label for="project-category">分類</label><select id="project-category" v-model="form.category"><option v-for="category in categories" :key="category">{{ category }}</option></select></div>
        <div class="admin-field"><label for="project-status">開發狀態</label><select id="project-status" v-model="form.status"><option>Planned</option><option>In Progress</option><option>Completed</option></select></div>
        <div class="admin-field"><label for="project-theme">封面配色</label><select id="project-theme" v-model="form.theme"><option value="forest">森林綠</option><option value="blue">藍色</option><option value="peach">暖橘</option><option value="violet">紫色</option></select></div>
        <label class="admin-check"><input v-model="form.featured" type="checkbox" />首頁精選</label><label class="admin-check"><input v-model="published" type="checkbox" />發布到公開頁</label>
        <div class="admin-field"><label for="project-sort">排序位置（不可重複，新增留空則排最後）</label><input id="project-sort" v-model="sortOrder" type="number" min="0" max="2147483646" /></div>
      </fieldset>
      <fieldset :disabled="busy || session?.role !== 'ADMIN'"><legend>作品內容</legend>
        <div class="admin-field"><label for="project-summary">摘要</label><textarea id="project-summary" v-model="form.summary" required maxlength="2000" rows="3" /></div>
        <div class="admin-field"><label for="project-description">作品說明</label><textarea id="project-description" v-model="form.description" required maxlength="20000" rows="6" /></div>
        <AdminTextList v-for="list in lists" :key="list.key" :id="`project-${list.key}`" :label="list.label" :model-value="form[list.key] ?? []" @update:model-value="form[list.key] = $event" />
        <p class="admin-note">現有技術標籤：{{ technologies.join('、') }}。可新增標籤；重複標籤會拒絕。</p>
        <div class="admin-field"><label for="project-architecture">架構說明</label><textarea id="project-architecture" v-model="form.architectureDescription" rows="4" maxlength="10000" /></div>
      </fieldset>
      <AdminPairList v-model="challenges" label="工程挑戰" first-label="挑戰標題" second-label="處理方式" :disabled="busy || session?.role !== 'ADMIN'" />
      <fieldset :disabled="busy || session?.role !== 'ADMIN'"><legend>來源與連結</legend>
        <div class="admin-field"><label for="project-github">GitHub 連結</label><input id="project-github" v-model="form.githubUrl" type="url" pattern="https://.*" maxlength="2048" /></div>
        <div class="admin-field"><label for="project-demo">公開 Demo 連結</label><input id="project-demo" v-model="form.liveDemoUrl" type="url" pattern="https://.*" maxlength="2048" /></div>
        <div class="admin-field"><label for="project-evidence-date">來源核對日期</label><input id="project-evidence-date" v-model="form.evidenceCheckedAt" type="date" :required="evidence.length > 0" /></div>
      </fieldset>
      <AdminPairList v-model="evidence" label="來源證據" first-label="證據名稱" second-label="HTTPS 來源連結" url :disabled="busy || session?.role !== 'ADMIN'" />
      <p v-if="managed?.project.coverImage || managed?.project.screenshots.length" class="admin-note">儲存文字時既有圖片會保留，請使用圖片管理修改圖片。</p>
      <div v-if="session?.role === 'ADMIN'" class="admin-actions"><button class="button button-dark" :disabled="busy" type="submit">{{ busy ? '處理中…' : '儲存作品' }}</button><button v-if="managed" class="button button-outline" type="button" :disabled="busy" @click="confirmDelete = true">刪除作品</button></div>
    </form>
    <div v-if="confirmDelete && managed" class="admin-panel admin-delete" role="group" aria-label="確認刪除作品"><p>確認刪除「{{ managed.project.title }}」？內容與圖片關聯將移除，稽核紀錄與網址代號會保留，代號不可重用。</p><div class="admin-actions"><button class="button button-dark" :disabled="busy" @click="remove">確認刪除</button><button class="button button-outline" :disabled="busy" @click="confirmDelete = false">取消</button></div></div>
  </section>
</template>

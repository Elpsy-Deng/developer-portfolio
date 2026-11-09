<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSession, AdminApiError, type AdminSession } from '../api/admin'
import { getManaged, type ManagedProject } from '../api/adminProjects'
import { getMedia, uploadMedia, editMedia, removeMedia, cleanupMedia, preview, type MediaItem } from '../api/media'
const route = useRoute(), router = useRouter()
const identity = ref<AdminSession | null>(null), project = ref<ManagedProject | null>(null), rows = ref<MediaItem[]>([])
const busy = ref(false), error = ref(''), success = ref(''), conflict = ref(false), failedImages = ref<Record<string, boolean>>({}), confirming = ref('')
const kind = ref('SCREENSHOT'), title = ref(''), description = ref(''), alt = ref(''), selected = ref<File | null>(null), fileInput = ref<HTMLInputElement | null>(null)
let epoch = 0
async function failure(e: unknown) {
  if (e instanceof AdminApiError && e.status === 401) { await router.replace('/admin/login'); return }
  conflict.value = e instanceof AdminApiError && e.status === 409
  error.value = e instanceof AdminApiError && e.status === 0 ? '操作結果尚未確認，請重新載入核對後再操作' : e instanceof Error ? e.message : '圖片操作失敗'
}
async function load() {
  const current = ++epoch; busy.value = true; error.value = ''; success.value = ''; conflict.value = false; project.value = null; rows.value = []; identity.value = null; failedImages.value = {}; confirming.value = ''; selected.value = null
  try {
    const session = await getSession()
    if (current !== epoch) return
    if (!session.authenticated) { await router.replace('/admin/login'); return }
    const slug = String(route.params.slug), [value, images] = await Promise.all([getManaged(slug), getMedia(slug)])
    if (current !== epoch) return
    identity.value = session; project.value = value; rows.value = images
  } catch (e) { if (current === epoch) await failure(e) }
  finally { if (current === epoch) busy.value = false }
}
function choose(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] ?? null; selected.value = null; error.value = ''
  if (file && (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 5242880)) { error.value = '只接受5MiB以內的PNG／JPEG，實際內容會由伺服器核對'; return }
  selected.value = file
}
async function mutate(operation: () => Promise<ManagedProject>, message: string, isUpload = false) {
  if (busy.value || identity.value?.role !== 'ADMIN' || !project.value) return
  const current = epoch; busy.value = true; error.value = ''; success.value = ''; conflict.value = false
  let committed = false
  try {
    const value = await operation(); committed = true
    if (current !== epoch) return
    project.value = value
    if (isUpload) { selected.value = null; if (fileInput.value) fileInput.value.value = '' }
    rows.value = []
    rows.value = await getMedia(value.project.slug); failedImages.value = {}; confirming.value = ''; success.value = message
  } catch (e) {
    if (current === epoch) { await failure(e); if (committed) error.value = '操作已儲存，但圖片列表讀取失敗。請重新載入核對。' }
  } finally { if (current === epoch) busy.value = false }
}
async function upload() {
  if (!project.value || !identity.value || !selected.value) return
  const slug = project.value.project.slug, version = project.value.version, session = identity.value, file = selected.value
  await mutate(() => uploadMedia(slug, version, { kind: kind.value, title: title.value, description: description.value, alt: alt.value, file }, session), '圖片已上傳', true)
}
async function save(item: MediaItem) {
  if (!project.value || !identity.value) return
  const p = project.value, session = identity.value
  await mutate(() => editMedia(p.project.slug, p.version, item, session), '圖片說明已儲存')
}
async function remove(item: MediaItem) {
  if (!project.value || !identity.value || confirming.value !== item.id) return
  const p = project.value, session = identity.value
  await mutate(() => removeMedia(p.project.slug, p.version, item.id, session), '圖片關聯已移除；未引用的上傳圖片將安全回收')
}
async function reclaim() {
  if (busy.value || identity.value?.role !== 'ADMIN') return
  busy.value = true; error.value = ''; success.value = ''
  try { const result = await cleanupMedia(identity.value); success.value = `回收完成：${result.retired} 個已移除圖片、${result.orphaned} 個過期孤兒素材、${result.temporary} 個暫存檔` }
  catch (e) { await failure(e) }
  finally { busy.value = false }
}
watch(() => route.fullPath, load, { immediate: true })
</script>
<template>
  <section class="container route-page admin-catalog">
    <div class="page-intro"><p class="eyebrow">PORTFOLIO / MEDIA</p><h1>圖片管理</h1><p>PNG／JPEG、原始檔最大5MiB、最長邊4096px，最多800萬像素；圖片內容將重編碼。</p></div>
    <div class="admin-actions"><RouterLink class="button button-outline" to="/admin/projects">返回作品列表</RouterLink><RouterLink class="button button-outline" :to="`/admin/projects/${route.params.slug}`">編輯作品</RouterLink><RouterLink class="button button-outline" :to="`/admin/projects/${route.params.slug}/audit`">稽核紀錄</RouterLink></div>
    <p v-if="busy && !project" role="status">正在讀取圖片…</p><p v-if="error" role="alert">{{ error }}</p><p v-if="success" role="status">{{ success }}</p>
    <div v-if="error" class="admin-actions"><p v-if="conflict">目前輸入已保留。重新載入會取代尚未儲存的圖片說明。</p><button class="button button-outline" :disabled="busy" @click="load">重新載入圖片</button></div>
    <template v-if="project">
      <p data-testid="media-version">{{ project.project.title }} · 目前版本 {{ project.version }} · {{ project.published ? '已發布' : '草稿：圖片僅供管理區預覽' }}</p>
      <form v-if="identity?.role === 'ADMIN'" class="admin-panel admin-edit-form" @submit.prevent="upload">
        <fieldset :disabled="busy"><legend>上傳圖片</legend>
          <div class="admin-field"><label for="image-kind">圖片用途</label><select id="image-kind" v-model="kind"><option value="SCREENSHOT">作品截圖</option><option value="COVER">封面（會取代目前封面）</option></select></div>
          <div class="admin-field"><label for="image-file">圖片檔案</label><input id="image-file" ref="fileInput" type="file" accept="image/png,image/jpeg" required @change="choose" /></div>
          <div class="admin-field"><label for="image-title">圖片標題</label><input id="image-title" v-model="title" :required="kind === 'SCREENSHOT'" maxlength="160" /></div>
          <div class="admin-field"><label for="image-description">圖片說明</label><textarea id="image-description" v-model="description" maxlength="5000" rows="3" /></div>
          <div class="admin-field"><label for="image-alt">替代文字</label><input id="image-alt" v-model="alt" required maxlength="300" /></div>
          <button class="button button-dark" :disabled="busy || !selected" type="submit">{{ busy ? '處理中…' : '上傳圖片' }}</button>
        </fieldset>
      </form>
      <p v-else class="admin-note">唯讀身份可以預覽圖片，不能上傳、修改或移除。</p>
      <p v-if="!rows.length && !error" class="admin-note">目前沒有圖片或圖片占位。</p>
      <ul class="admin-media-list">
        <li v-for="item in rows" :key="item.id" class="admin-panel admin-edit-form" :data-media-id="item.id">
          <h2>{{ item.kind === 'COVER' ? '封面' : '作品截圖' }}</h2>
          <template v-if="item.src"><img v-if="!failedImages[item.id]" :src="preview(item.src)" :alt="item.alt || item.title || '作品圖片'" @error="failedImages[item.id] = true" /><p v-else>圖片暫時無法讀取，請重新載入核對。</p></template><p v-else>尚未上傳圖片的占位。</p>
          <p class="admin-note">{{ item.managed ? `${item.width} × ${item.height}px · ${Math.ceil((item.bytes ?? 0) / 1024)}KiB` : '既有靜態素材或占位；移除只解除關聯。' }}</p>
          <form @submit.prevent="save(item)"><fieldset :disabled="busy || identity?.role !== 'ADMIN'"><legend>圖片資訊</legend>
            <label>媒體標題<input v-model="item.title" :required="item.kind === 'SCREENSHOT'" maxlength="160" /></label>
            <label>媒體說明<textarea v-model="item.description" rows="3" maxlength="5000" /></label>
            <label>媒體替代文字<input v-model="item.alt" :required="!!item.src" maxlength="300" /></label>
            <label>媒體排序<input v-model.number="item.position" type="number" min="0" :readonly="item.kind === 'COVER'" required /></label>
            <div v-if="identity?.role === 'ADMIN'" class="admin-actions"><button class="button button-dark" :disabled="busy" type="submit">儲存圖片說明</button><button class="button button-outline" :disabled="busy" type="button" @click="confirming = item.id">移除圖片</button></div>
          </fieldset></form>
          <div v-if="confirming === item.id" class="admin-delete"><p>確認移除此圖片關聯？上傳圖片沒有其他引用時會回收；稽核保留說明與來源。</p><div class="admin-actions"><button class="button button-dark" :disabled="busy" @click="remove(item)">確認移除圖片</button><button class="button button-outline" :disabled="busy" @click="confirming = ''">取消</button></div></div>
        </li>
      </ul>
      <button v-if="identity?.role === 'ADMIN'" class="button button-outline" :disabled="busy" @click="reclaim">回收未引用素材</button>
    </template>
  </section>
</template>

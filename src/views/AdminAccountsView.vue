<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AdminApiError, getSession, type AdminSession } from '../api/admin'
import { listAccounts, listIdentityAudit, createAccount, updateAccount, type Account, type IdentityAudit } from '../api/accounts'
const router = useRouter()
const session = ref<AdminSession | null>(null)
const rows = ref<Account[]>([])
const audit = ref<IdentityAudit[]>([])
const selected = ref<Account | null>(null)
const editRole = ref<Account['role']>('VIEWER')
const editEnabled = ref(true)
const confirmed = ref(false)
const newName = ref(''), newPassword = ref('')
const newRole = ref<Account['role']>('VIEWER'), newEnabled = ref(true)
const busy = ref(false), error = ref(''), notice = ref('')
const conflict = ref(false)
function choose(row: Account) { selected.value = { ...row }; editRole.value = row.role; editEnabled.value = row.enabled; confirmed.value = false; conflict.value = false; error.value = '' }
async function fail(e: unknown) {
  if (e instanceof AdminApiError && e.status === 401) { rows.value = []; audit.value = []; session.value = null; await router.replace('/admin/login'); return }
  conflict.value = e instanceof AdminApiError && e.status === 409
  error.value = e instanceof Error ? e.message : '帳號管理暫時無法使用'
  if (e instanceof AdminApiError && e.status === 0) error.value += '；提交結果尚未確認，請先重新核對，不要直接重送。'
}
async function load() {
  busy.value = true; error.value = ''; notice.value = ''; rows.value = []; audit.value = []; session.value = null
  try {
    const identity = await getSession()
    if (!identity.authenticated) { await router.replace('/admin/login'); return }
    if (identity.role !== 'ADMIN') { error.value = '你沒有執行此操作的權限'; return }
    const [accounts, history] = await Promise.all([listAccounts(), listIdentityAudit()])
    session.value = identity; rows.value = accounts; audit.value = history
  } catch (e) { await fail(e) } finally { busy.value = false }
}
async function latest() {
  if (!selected.value) return
  const name = selected.value.username
  await load()
  const row = rows.value.find(r => r.username === name)
  if (row) choose(row)
}
async function afterWrite(message: string) {
  selected.value = null
  await load()
  if (session.value) notice.value = message
  else if (error.value) error.value = `${message} 清單讀取失敗：${error.value}`
}
async function create() {
  if (!session.value || busy.value) return
  busy.value = true; error.value = ''; notice.value = ''; conflict.value = false
  try {
    await createAccount({ username: newName.value, password: newPassword.value, role: newRole.value, enabled: newEnabled.value }, session.value)
    newName.value = ''; newPassword.value = ''
    await afterWrite('帳號已建立，請透過受信任管道交付初始密碼。')
  } catch (e) { await fail(e) } finally { newPassword.value = ''; busy.value = false }
}
async function save() {
  if (!session.value || !selected.value || busy.value || !confirmed.value) return
  busy.value = true; error.value = ''; notice.value = ''; conflict.value = false
  try {
    const beforeVersion = selected.value.version
    const updated = await updateAccount(selected.value.username, { expectedVersion: selected.value.version, role: editRole.value, enabled: editEnabled.value }, session.value)
    await afterWrite(updated.version === beforeVersion ? '內容未變更，版本與登入保持。' : '帳號已更新，原有登入已撤銷。')
  } catch (e) { await fail(e) } finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <section class="container route-page admin-page">
    <div class="page-intro"><p class="eyebrow">PORTFOLIO / ACCOUNTS</p><h1>帳號管理</h1><p>管理角色與啟用狀態；必須保留至少一位啟用中的管理員。</p></div>
    <RouterLink class="button button-outline" to="/admin">管理概覽</RouterLink>
    <p v-if="busy" role="status">正在核對帳號…</p><p v-if="error" role="alert">{{ error }}</p><p v-if="notice" role="status">{{ notice }}</p>
    <button v-if="error && !conflict" class="button button-outline" :disabled="busy" @click="load">重新載入帳號</button>
    <template v-if="session">
      <form class="admin-panel admin-form" @submit.prevent="create">
        <h2>新增帳號</h2><p>請先用密碼管理器保存初始密碼；建立後不會再顯示密碼。</p><label for="new-account">帳號代號</label><input id="new-account" v-model="newName" pattern="[a-z][a-z0-9_-]{2,63}" maxlength="64" required :disabled="busy" autocomplete="off" />
        <label for="new-secret">初始密碼</label><input id="new-secret" v-model="newPassword" type="password" minlength="12" maxlength="256" required :disabled="busy" autocomplete="new-password" />
        <label for="new-role">新增角色</label><select id="new-role" v-model="newRole" :disabled="busy"><option value="VIEWER">唯讀檢視者</option><option value="ADMIN">管理員</option></select>
        <label><input v-model="newEnabled" type="checkbox" :disabled="busy" />新增帳號啟用</label><button class="button button-dark" :disabled="busy" type="submit">建立帳號</button>
      </form>
      <ul class="admin-project-list"><li v-for="row in rows" :key="row.username" :data-account="row.username"><div><h2>{{ row.username }}</h2><p>{{ row.role }} · {{ row.enabled ? '啟用' : '停用' }} · 版本 {{ row.version }}</p></div><button class="button button-outline" :disabled="busy" @click="choose(row)">管理 {{ row.username }}</button></li></ul>
      <form v-if="selected" class="admin-panel admin-form" @submit.prevent="save">
        <h2>編輯 {{ selected.username }}</h2><p>目前核對版本 {{ selected.version }}；變更角色或啟用狀態會撤銷原有登入。修改自己的帳號後需重新登入。</p>
        <label for="edit-role">編輯角色</label><select id="edit-role" v-model="editRole" :disabled="busy"><option value="VIEWER">唯讀檢視者</option><option value="ADMIN">管理員</option></select>
        <label><input v-model="editEnabled" type="checkbox" :disabled="busy" />帳號啟用</label><label><input v-model="confirmed" type="checkbox" :disabled="busy" />確認角色與啟用變更</label>
        <button type="submit" class="button button-dark" :disabled="busy || !confirmed">儲存帳號變更</button><button v-if="conflict" type="button" class="button button-outline" :disabled="busy" @click="latest">載入最新帳號版本</button>
      </form>
      <div class="admin-panel"><h2>最近帳號稽核</h2><p v-if="!audit.length">尚無帳號稽核</p><ul><li v-for="entry in audit" :key="entry.id">{{ entry.action }} · {{ entry.username }} · 操作者 {{ entry.actor }} · {{ entry.createdAt }}<br />{{ entry.before ? `${entry.before.role}/${entry.before.enabled ? '啟用' : '停用'}/v${entry.before.version}` : '新帳號' }} → {{ entry.after.role }}/{{ entry.after.enabled ? '啟用' : '停用' }}/v{{ entry.after.version }}</li></ul></div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AdminApiError, getSession, type AdminSession } from '../api/admin'
import { getPasswordProfile, changePassword, type PasswordProfile } from '../api/password'
const router = useRouter()
const session = ref<AdminSession | null>(null), profile = ref<PasswordProfile | null>(null)
const current = ref(''), next = ref(''), confirm = ref('')
const busy = ref(false), done = ref(false), error = ref(''), remaining = ref(0)
let retryUntil = 0
const timer = window.setInterval(() => { remaining.value = Math.max(0, Math.ceil((retryUntil - Date.now()) / 1000)) }, 250)
function clear() { current.value = ''; next.value = ''; confirm.value = '' }
onUnmounted(() => { window.clearInterval(timer); clear() })
async function load() {
  busy.value = true; error.value = ''; session.value = null; profile.value = null; clear()
  try {
    const identity = await getSession()
    if (!identity.authenticated) { await router.replace('/admin/login'); return }
    const account = await getPasswordProfile()
    if (account.username !== identity.username || account.role !== identity.role) throw new AdminApiError('身份資料已變更，請重新登入', 401)
    session.value = identity; profile.value = account
  } catch (e) {
    if (e instanceof AdminApiError && e.status === 401) await router.replace('/admin/login')
    else error.value = e instanceof Error ? e.message : '身份資料讀取失敗'
  } finally { busy.value = false }
}
async function submit() {
  if (!session.value || !profile.value || busy.value || remaining.value > 0) return
  if (next.value !== confirm.value) { error.value = '兩次新密碼不一致'; clear(); return }
  busy.value = true; error.value = ''
  try {
    await changePassword(profile.value, current.value, next.value, session.value)
    done.value = true; session.value = null; profile.value = null
  } catch (e) {
    if (e instanceof AdminApiError && e.status === 401) { await router.replace('/admin/login'); return }
    error.value = e instanceof Error ? e.message : '密碼更改失敗'
    if (e instanceof AdminApiError && e.status === 429) { remaining.value = e.retryAfterSeconds; retryUntil = Date.now() + remaining.value * 1000 }
    if (e instanceof AdminApiError && (e.status === 0 || e.status === 503)) error.value += '；結果尚未確認，請重新登入核對，不要直接重送。'
  } finally { clear(); busy.value = false }
}
onMounted(load)
</script>
<template>
  <section class="container route-page admin-page">
    <div class="page-intro"><p class="eyebrow">PORTFOLIO / PASSWORD</p><h1>更改我的密碼</h1><p>驗證目前密碼後更改；成功後所有裝置都需重新登入。</p></div>
    <div v-if="done" class="admin-panel"><p role="status">密碼已更改，所有舊登入已撤銷。請使用新密碼重新登入。</p><RouterLink class="button button-dark" to="/admin/login">使用新密碼登入</RouterLink></div>
    <template v-else>
      <RouterLink class="button button-outline" to="/admin">管理概覽</RouterLink>
      <p v-if="busy && !profile" role="status">正在核對身份…</p><p v-if="error" role="alert">{{ error }}</p><p v-if="remaining > 0" role="status">請等待 {{ remaining }} 秒後再手動操作。</p>
      <button v-if="error" class="button button-outline" :disabled="busy" @click="load">重新核對身份</button>
      <form v-if="profile && session" class="admin-panel admin-form" @submit.prevent="submit">
        <p>目前帳號：{{ profile.username }} · 版本 {{ profile.version }}</p>
        <label for="current-secret">目前密碼</label><input id="current-secret" v-model="current" type="password" autocomplete="current-password" maxlength="256" required :disabled="busy" />
        <label for="next-secret">新密碼</label><input id="next-secret" v-model="next" type="password" autocomplete="new-password" minlength="12" maxlength="256" required :disabled="busy" />
        <label for="confirm-secret">再次輸入新密碼</label><input id="confirm-secret" v-model="confirm" type="password" autocomplete="new-password" minlength="12" maxlength="256" required :disabled="busy" />
        <button class="button button-dark" type="submit" :disabled="busy || remaining > 0">更改密碼並登出所有裝置</button>
      </form>
    </template>
  </section>
</template>

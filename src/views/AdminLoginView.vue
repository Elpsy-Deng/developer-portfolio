<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AdminApiError, getSession, login, type AdminSession } from '../api/admin'
const router = useRouter()
const session = ref<AdminSession | null>(null)
const username = ref('')
const password = ref('')
const busy = ref(false)
const error = ref('')
const remaining = ref(0)
let retryUntil = 0
const timer = window.setInterval(() => { remaining.value = Math.max(0, Math.ceil((retryUntil - Date.now()) / 1000)) }, 250)
onUnmounted(() => window.clearInterval(timer))
async function load() {
  if (busy.value || (remaining.value > 0 && !session.value)) return
  busy.value = true; error.value = ''; session.value = null
  try {
    session.value = await getSession()
    if (session.value.authenticated) await router.replace('/admin')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '管理服務暫時無法使用'
    if (e instanceof AdminApiError && e.status === 429) {
      remaining.value=e.retryAfterSeconds;retryUntil=Date.now()+remaining.value*1000
    }
  }
  finally { busy.value = false }
}
async function submit() {
  if (!session.value || busy.value || remaining.value > 0) return
  busy.value = true; error.value = ''
  try {
    await login(username.value, password.value, session.value)
    password.value = ''
    await router.replace('/admin')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登入失敗'; password.value = ''
    if (e instanceof AdminApiError && e.status === 429) {
      remaining.value = e.retryAfterSeconds; retryUntil = Date.now() + remaining.value * 1000
    }
  }
  finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <section class="container route-page admin-page">
    <div class="page-intro"><p class="eyebrow">PORTFOLIO / ADMIN</p><h1>管理登入</h1><p>使用已建立的管理帳號登入，公開作品頁無需登入。</p></div>
    <form class="admin-panel admin-form" @submit.prevent="submit">
      <p v-if="remaining > 0" role="status">請等待 {{ remaining }} 秒後再手動登入。</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <p v-if="busy && !session" role="status">正在連線管理服務…</p>
      <label for="admin-username">帳號</label><input id="admin-username" v-model="username" autocomplete="username" maxlength="64" required :disabled="busy || !session" />
      <label for="admin-password">密碼</label><input id="admin-password" v-model="password" type="password" autocomplete="current-password" maxlength="256" required :disabled="busy || !session" />
      <button class="button button-dark" type="submit" :disabled="busy || !session || remaining > 0">{{ busy ? '處理中…' : '登入' }}</button>
      <button v-if="error" class="button button-outline" type="button" :disabled="busy || (remaining > 0 && !session)" @click="load">重新載入驗證</button>
    </form>
  </section>
</template>

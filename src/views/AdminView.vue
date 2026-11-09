<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { AdminApiError, getOverview, getSecurity, getSession, logout, type AdminSecurity, type AdminSession } from '../api/admin'
const router = useRouter()
const session = ref<AdminSession | null>(null)
const count = ref<number | null>(null)
const security = ref<AdminSecurity | null>(null)
const busy = ref(false)
const error = ref('')
async function failure(e: unknown) {
  session.value = null; count.value = null; security.value = null
  if (e instanceof AdminApiError && e.status === 401) { await router.replace('/admin/login'); return }
  error.value = e instanceof Error ? e.message : '管理服務暫時無法使用'
}
async function load() {
  busy.value = true; error.value = ''; session.value = null; count.value = null; security.value = null
  try {
    const identity = await getSession()
    if (!identity.authenticated) { await router.replace('/admin/login'); return }
    const overview = await getOverview()
    const settings = identity.role === 'ADMIN' ? await getSecurity() : null
    session.value = identity; count.value = overview; security.value = settings
  } catch (e) { await failure(e) }
  finally { busy.value = false }
}
async function signOut() {
  if (!session.value || busy.value) return
  busy.value = true; error.value = ''
  try { await logout(session.value); session.value = null; await router.replace('/admin/login') }
  catch (e) { await failure(e) }
  finally { busy.value = false }
}
onMounted(load)
</script>
<template>
  <section class="container route-page admin-page">
    <div class="page-intro"><p class="eyebrow">PORTFOLIO / ADMIN</p><h1>管理概覽</h1><p>核對登入身份與目前可用的管理權限。</p></div>
    <div class="admin-panel">
      <p v-if="busy" role="status">正在讀取管理資料…</p>
      <template v-if="session">
        <p>登入帳號：<strong>{{ session.username }}</strong></p>
        <p>角色：<strong>{{ session.role === 'ADMIN' ? '管理員' : '唯讀檢視者' }}</strong></p>
        <p>已公開作品：<strong>{{ count }}</strong></p>
        <p v-if="security" data-testid="security-summary">啟用帳號 {{ security.enabledAccounts }} 個；閒置 {{ security.idleTimeoutMinutes }} 分鐘或登入滿 {{ security.absoluteTimeoutSeconds / 3600 }} 小時後需重新登入。<br />登入上限：每 {{ security.loginWindowSeconds }} 秒，帳號 {{ security.loginAccountLimit }} 次、連線來源 {{ security.loginPeerLimit }} 次、全站 {{ security.loginGlobalLimit }} 次。</p>
        <p v-else>你可以查看管理概覽；帳號安全資訊僅限管理員。</p>
        <p class="admin-note">管理作品內容、草稿與發布；圖片上傳及說明請由作品的圖片管理進入。</p>
        <RouterLink class="button button-outline" to="/admin/taxonomy">分類與技術管理</RouterLink>
        <RouterLink class="button button-outline" to="/admin/password">更改我的密碼</RouterLink>
        <RouterLink v-if="session.role === 'ADMIN'" class="button button-outline" to="/admin/accounts">帳號管理</RouterLink>
        <RouterLink class="button button-dark" to="/admin/projects">作品管理</RouterLink>
        <button class="button button-outline" :disabled="busy" @click="signOut">登出</button>
      </template>
      <p v-if="error" role="alert">{{ error }}</p>
      <button v-if="error" class="button button-dark" :disabled="busy" @click="load">重新載入</button>
    </div>
  </section>
</template>

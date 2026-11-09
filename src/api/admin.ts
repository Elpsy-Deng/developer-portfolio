export type AdminSession = {
  authenticated: boolean
  username: string
  role: '' | 'ADMIN' | 'VIEWER'
  csrf: { headerName: string; token: string }
}
export class AdminApiError extends Error {
  constructor(message: string, public status = 0, public retryAfterSeconds = 0) { super(message) }
}
export async function adminRequest(path: string, options: RequestInit = {}): Promise<Response> {
  let response: Response
  try {
    response = await fetch(`/api/admin/${path}`, {
      ...options, credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.any([AbortSignal.timeout(8000), ...(options.signal ? [options.signal] : [])]),
    })
  } catch { throw new AdminApiError('管理服務暫時無法連線，請稍後重試') }
  if (!response.ok) {
    const messages: Record<string, string> = {
      INVALID_CREDENTIALS: '帳號或密碼不正確',
      INVALID_QUERY: '搜尋或分頁條件不正確', INVALID_TERM: '分類或技術欄位不正確', TERM_NOT_FOUND: '找不到分類或技術',
      TERM_EXISTS: '分類或技術名稱已存在', TERM_ORDER_CONFLICT: '分類排序位置已被使用',
      TERM_VERSION_CONFLICT: '分類或技術已更新，請核對最新版本', TERM_IN_USE: '仍被作品引用，不能刪除',
      TERM_RENAME_LIMIT: '一次改名最多影響100個作品', TERM_LIMIT: '分類或技術項目已達500筆上限',
      PASSWORD_INVALID: '目前密碼不正確', PASSWORD_REUSED: '新密碼必須與目前密碼不同',
      INVALID_PASSWORD_CHANGE: '密碼欄位不正確；新密碼需12至256字元',
      PASSWORD_VERSION_CONFLICT: '身份資料已更新，請重新核對', PASSWORD_BUSY: '身份資料正在更新，請重新核對',
      PASSWORD_RATE_LIMITED: '密碼更改嘗試過於頻繁，請稍後再試',
      ACCOUNT_NOT_FOUND: '找不到帳號', INVALID_ACCOUNT: '帳號欄位不正確；密碼需12至256字元',
      ACCOUNT_EXISTS: '帳號代號已使用', ACCOUNT_LIMIT: '最多建立100個管理帳號',
      ACCOUNT_VERSION_CONFLICT: '帳號已被更新，請核對最新版本', LAST_ADMIN: '必須保留至少一位啟用中的管理員',
      IDENTITY_BUSY: '帳號管理正在更新，請重新核對後再試', UNAUTHENTICATED: '登入已失效，請重新登入',
      FORBIDDEN: '你沒有執行此操作的權限', CSRF_INVALID: '驗證已失效，請重新載入後操作',
      VERSION_CONFLICT: '作品已被更新，請核對最新版本後再儲存', SLUG_EXISTS: '作品網址代號已使用，請選擇新代號',
      ORDER_CONFLICT: '排序位置已被使用，請選擇其他位置', INVALID_PROJECT: '作品欄位不正確，請核對內容',
      INVALID_CATEGORY: '分類不正確', INVALID_VERSION: '作品版本不正確，請重新載入', IMMUTABLE_SLUG: '既有作品網址代號不能更改',
      INVALID_IMAGE: '圖片內容或尺寸不正確；只接受 PNG／JPEG、最長邊 4096px、最多 800 萬像素',
      INVALID_MEDIA: '請核對圖片欄位', IMAGE_TOO_LARGE: '圖片不可超過 5MiB', IMAGE_BUSY: '圖片處理中，請稍後重試',
      IMAGE_STORAGE_UNAVAILABLE: '圖片儲存暫時無法使用，請稍後重試', MEDIA_NOT_FOUND: '找不到這張圖片',
      MEDIA_ORDER_CONFLICT: '圖片排序位置已被使用', MEDIA_LIMIT: '每個作品最多 20 張截圖',
      PROJECT_NOT_FOUND: '找不到這個作品', CATALOG_UNAVAILABLE: '作品資料暫時無法讀取，請稍後重試',
      AUTH_UNAVAILABLE: '登入服務暫時無法使用，請稍後重試',
      LOGIN_RATE_LIMITED: '登入嘗試過於頻繁，請稍後再試',
      SESSION_RATE_LIMITED: '工作階段建立過於頻繁，請稍後再試',
    }
    let code = ''
    try { const body: unknown = await response.json(); if (body && typeof body === 'object' && 'code' in body && typeof body.code === 'string') code = body.code } catch { /* Never show raw server responses. */ }
    const retry = Number(response.headers.get('Retry-After'))
    const wait = response.status === 429 ? (Number.isInteger(retry) && retry > 0 && retry <= 3600 ? retry : 60) : 0
    throw new AdminApiError(messages[code] ?? '管理服務回應不正確，請稍後重試', response.status, wait)
  }
  return response
}
export async function getSession(): Promise<AdminSession> {
  const value: unknown = await (await adminRequest('session')).json()
  if (!value || typeof value !== 'object' || !('authenticated' in value) || typeof value.authenticated !== 'boolean'
      || !('username' in value) || typeof value.username !== 'string' || !('role' in value) || !['', 'ADMIN', 'VIEWER'].includes(String(value.role))
      || !('csrf' in value) || !value.csrf || typeof value.csrf !== 'object'
      || !('headerName' in value.csrf) || value.csrf.headerName !== 'X-CSRF-TOKEN'
      || !('token' in value.csrf) || typeof value.csrf.token !== 'string' || value.csrf.token.length < 32
      || (value.authenticated ? !value.username || !value.role : value.username !== '' || value.role !== '')) {
    throw new AdminApiError('管理服務回應不完整，請重新載入')
  }
  return value as AdminSession
}
export async function login(username: string, password: string, session: AdminSession): Promise<void> {
  await adminRequest('login', { method: 'POST', headers: { [session.csrf.headerName]: session.csrf.token },
    body: new URLSearchParams({ username, password }) })
}
export async function logout(session: AdminSession): Promise<void> {
  await adminRequest('logout', { method: 'POST', headers: { [session.csrf.headerName]: session.csrf.token } })
}
export async function getOverview(): Promise<number> {
  const value: unknown = await (await adminRequest('overview')).json()
  if (!value || typeof value !== 'object' || !('publishedProjects' in value) || !Number.isSafeInteger(value.publishedProjects) || Number(value.publishedProjects) < 0) throw new AdminApiError('管理服務回應不完整')
  return Number(value.publishedProjects)
}
export type AdminSecurity = { enabledAccounts: number; idleTimeoutMinutes: number; absoluteTimeoutSeconds: number;
  loginWindowSeconds: number; loginAccountLimit: number; loginPeerLimit: number; loginGlobalLimit: number }
export async function getSecurity(): Promise<AdminSecurity> {
  const value: unknown = await (await adminRequest('security')).json()
  if (!value || typeof value !== 'object' || !('enabledAccounts' in value) || !Number.isSafeInteger(value.enabledAccounts)
      || Number(value.enabledAccounts) < 0 || !('idleTimeoutMinutes' in value) || value.idleTimeoutMinutes !== 30
      || !['absoluteTimeoutSeconds', 'loginWindowSeconds', 'loginAccountLimit', 'loginPeerLimit', 'loginGlobalLimit']
        .every(key => key in value && Number.isSafeInteger((value as Record<string, unknown>)[key]) && Number((value as Record<string, unknown>)[key]) > 0)) throw new AdminApiError('管理服務回應不完整')
  return value as AdminSecurity
}

import { test, expect, type Page, type APIRequestContext } from '@playwright/test'
function credentials(role: 'ADMIN' | 'VIEWER') {
  const username = process.env[`PORTFOLIO_E2E_${role}_USERNAME`], password = process.env[`PORTFOLIO_E2E_${role}_PASSWORD`]
  if (!username || !password) throw new Error('Use own private runtime accounts')
  return { username, password }
}
async function login(page: Page, role: 'ADMIN' | 'VIEWER' = 'ADMIN') {
  await page.goto('/admin/login')
  await page.getByLabel('帳號', { exact: true }).fill(credentials(role).username)
  await page.getByLabel('密碼', { exact: true }).fill(credentials(role).password)
  await page.getByRole('button', { name: '登入', exact: true }).click()
  await expect(page).toHaveURL(/\/admin$/)
}
async function csrf(api: APIRequestContext) {
  const response = await api.get('/api/admin/session')
  if (response.status() !== 200) test.info().annotations.push({ type: 'session-http-status', description: String(response.status()) })
  expect(response.status()).toBe(200)
  return (await response.json()).csrf.token as string
}
async function apiCreate(api: APIRequestContext, name: string) {
  const r = await api.post('/api/admin/accounts', { headers: { 'X-CSRF-TOKEN': await csrf(api) }, data: { username: name, password: credentials('VIEWER').password, role: 'VIEWER', enabled: true } })
  expect(r.status()).toBe(201)
}
test.beforeEach(({ page }) => { page.on('pageerror', e => { throw e }) })

test('帳號 UI 新增、停用、重新啟用與安全稽核，初始密碼清除', async ({ page, context }) => {
  await login(page)
  await page.getByRole('link', { name: '帳號管理', exact: true }).click()
  await page.getByLabel('帳號代號', { exact: true }).fill('browser_account_lifecycle')
  await page.getByLabel('初始密碼', { exact: true }).fill(credentials('VIEWER').password)
  await page.getByRole('button', { name: '建立帳號', exact: true }).click()
  await expect(page.getByLabel('初始密碼', { exact: true })).toHaveValue('')
  await expect(page.locator('[data-account="browser_account_lifecycle"]')).toContainText('版本 1')
  await page.getByRole('button', { name: '管理 browser_account_lifecycle', exact: true }).click()
  await page.getByLabel('帳號啟用', { exact: true }).uncheck()
  await page.getByLabel('確認角色與啟用變更', { exact: true }).check()
  await page.getByRole('button', { name: '儲存帳號變更', exact: true }).click()
  await expect(page.locator('[data-account="browser_account_lifecycle"]')).toContainText('停用 · 版本 2')
  await page.getByRole('button', { name: '管理 browser_account_lifecycle', exact: true }).click()
  await page.getByLabel('帳號啟用', { exact: true }).check()
  await page.getByLabel('確認角色與啟用變更', { exact: true }).check()
  await page.getByRole('button', { name: '儲存帳號變更', exact: true }).click()
  await expect(page.locator('[data-account="browser_account_lifecycle"]')).toContainText('啟用 · 版本 3')
  const audit = await (await context.request.get('/api/admin/accounts/audit')).json()
  expect(audit.filter((a: { username: string }) => a.username === 'browser_account_lifecycle')).toHaveLength(3)
  expect(JSON.stringify(audit)).not.toContain(credentials('VIEWER').password)
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
})

test('帳號兩頁版本衝突保留角色輸入並要求核對最新版本', async ({ page, context }) => {
  await login(page); await apiCreate(context.request, 'browser_account_conflict')
  const other = await context.newPage()
  try {
    for (const p of [page, other]) { await p.goto('/admin/accounts'); await p.getByRole('button', { name: '管理 browser_account_conflict', exact: true }).click() }
    await page.getByLabel('帳號啟用', { exact: true }).uncheck()
    await page.getByLabel('確認角色與啟用變更', { exact: true }).check()
    await page.getByRole('button', { name: '儲存帳號變更', exact: true }).click()
    await expect(page.locator('[data-account="browser_account_conflict"]')).toContainText('版本 2')
    await other.getByLabel('編輯角色', { exact: true }).selectOption('ADMIN')
    await other.getByLabel('確認角色與啟用變更', { exact: true }).check()
    await other.getByRole('button', { name: '儲存帳號變更', exact: true }).click()
    await expect(other.getByRole('alert')).toContainText('帳號已被更新')
    await expect(other.getByLabel('編輯角色', { exact: true })).toHaveValue('ADMIN')
    await other.getByRole('button', { name: '載入最新帳號版本' }).click()
    await expect(other.getByLabel('編輯角色', { exact: true })).toHaveValue('VIEWER')
    await expect(other.getByLabel('帳號啟用', { exact: true })).not.toBeChecked()
  } finally { await other.close() }
})

test('唯讀角色不看帳號目錄，唯一啟用管理員受後端保護', async ({ page, context }) => {
  await login(page)
  await page.goto('/admin/accounts')
  await page.getByRole('button', { name: `管理 ${credentials('ADMIN').username}`, exact: true }).click()
  await page.getByLabel('編輯角色', { exact: true }).selectOption('VIEWER')
  await page.getByLabel('確認角色與啟用變更', { exact: true }).check()
  await page.getByRole('button', { name: '儲存帳號變更', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('必須保留至少一位啟用中的管理員')
  expect((await (await context.request.get('/api/admin/session')).json()).authenticated).toBe(true)
  await page.getByRole('link', { name: '管理概覽', exact: true }).click()
  await page.getByRole('button', { name: '登出', exact: true }).click()
  await login(page, 'VIEWER')
  await expect(page.getByRole('link', { name: '帳號管理', exact: true })).toHaveCount(0)
  await page.goto('/admin/accounts')
  await expect(page.getByRole('alert')).toContainText('你沒有執行此操作的權限')
  await expect(page.getByRole('button', { name: '建立帳號', exact: true })).toHaveCount(0)
  expect((await context.request.get('/api/admin/accounts')).status()).toBe(403)
})

test('帳號 503 保留非秘密輸入且可手動核對恢復，手機390px不溢出', async ({ page }, testInfo) => {
  await login(page); await page.setViewportSize({ width: 390, height: 850 }); await page.goto('/admin/accounts')
  await page.getByLabel('帳號代號', { exact: true }).fill('browser_account_fail')
  await page.getByLabel('初始密碼', { exact: true }).fill(credentials('VIEWER').password)
  await page.route('**/api/admin/accounts', route => route.request().method() === 'POST' ? route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"AUTH_UNAVAILABLE"}' }) : route.continue())
  await page.getByRole('button', { name: '建立帳號', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('登入服務暫時無法使用')
  await expect(page.getByLabel('初始密碼', { exact: true })).toHaveValue('')
  await expect(page.getByLabel('帳號代號', { exact: true })).toHaveValue('browser_account_fail')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('accounts-390.png'), fullPage: true })
  await page.unroute('**/api/admin/accounts')
  await page.getByRole('button', { name: '重新載入帳號' }).click()
  await expect(page.getByRole('button', { name: '建立帳號', exact: true })).toBeEnabled()
})

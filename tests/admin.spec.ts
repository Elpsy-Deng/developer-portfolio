import { test, expect, type Page } from '@playwright/test'

function credentials(role: 'ADMIN' | 'VIEWER') {
  const username = process.env[`PORTFOLIO_E2E_${role}_USERNAME`]
  const password = process.env[`PORTFOLIO_E2E_${role}_PASSWORD`]
  if (!username || !password) throw new Error('Use the owned runtime harness to provision private test accounts')
  return { username, password }
}
async function signIn(page: Page, role: 'ADMIN' | 'VIEWER') {
  await page.goto('/admin/login')
  const account = credentials(role)
  await page.getByLabel('帳號', { exact: true }).fill(account.username)
  await page.getByLabel('密碼', { exact: true }).fill(account.password)
  await page.getByRole('button', { name: '登入', exact: true }).click()
  await expect(page).toHaveURL(/\/admin$/)
  await expect(page.getByText(role === 'ADMIN' ? '管理員' : '唯讀檢視者', { exact: true })).toBeVisible()
}
test.beforeEach(({ page }) => { page.on('pageerror', error => { throw error }) })

test('管理深連結、錯誤密碼、登入、重載與登出使用真實 Session', async ({ page, context }) => {
  await page.goto('/admin')
  await expect(page).toHaveURL(/\/admin\/login$/)
  await expect(page.getByRole('heading', { name: '管理登入' })).toBeVisible()
  await page.getByLabel('帳號', { exact: true }).fill(credentials('ADMIN').username)
  await page.getByLabel('密碼', { exact: true }).fill('wrong-password')
  await page.getByRole('button', { name: '登入', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('帳號或密碼不正確')
  await expect(page.getByLabel('密碼', { exact: true })).toHaveValue('')
  await signIn(page, 'ADMIN')
  await expect(page.getByText('已公開作品：')).toContainText('5')
  await expect(page.getByTestId('security-summary')).toContainText('30 分鐘')
  await page.reload()
  await expect(page.getByText('管理員', { exact: true })).toBeVisible()
  const cookie = (await context.cookies()).find(c => c.name === 'PORTFOLIO_SESSION')
  expect(cookie?.httpOnly).toBe(true); expect(cookie?.sameSite).toBe('Strict'); expect(cookie?.path).toBe('/api/admin')
  await page.getByRole('button', { name: '登出', exact: true }).click()
  await expect(page).toHaveURL(/\/admin\/login$/)
  expect((await context.request.get('/api/admin/overview')).status()).toBe(401)
  await page.goto('/admin')
  await expect(page).toHaveURL(/\/admin\/login$/)
})

test('唯讀身份由後端拒絕管理員資訊，公開列表保持可讀', async ({ page, context }) => {
  await signIn(page, 'VIEWER')
  await expect(page.getByTestId('security-summary')).toHaveCount(0)
  const response = await context.request.get('/api/admin/security?role=ADMIN')
  expect(response.status()).toBe(403); expect((await response.json()).code).toBe('FORBIDDEN')
  expect((await context.request.get('/api/admin/overview')).status()).toBe(200)
  await page.goto('/projects')
  await expect(page.locator('.project-card')).toHaveCount(5)
})

test('瀏覽器 Cookie 無法取代登入與登出的 CSRF 驗證', async ({ page, context }) => {
  await page.goto('/admin/login')
  await expect(page.getByRole('button', { name: '登入', exact: true })).toBeEnabled()
  const session = await (await context.request.get('/api/admin/session')).json()
  const missing = await context.request.post('/api/admin/login', { form: credentials('ADMIN') })
  expect(missing.status()).toBe(403)
  await signIn(page, 'ADMIN')
  expect((await context.request.post('/api/admin/logout')).status()).toBe(403)
  const stale = await context.request.post('/api/admin/logout', { headers: { 'X-CSRF-TOKEN': session.csrf.token } })
  expect(stale.status()).toBe(403)
  await page.reload()
  await expect(page.getByText('管理員', { exact: true })).toBeVisible()
})

test('管理服務 503 阻止提交，明確重載後恢復真服務', async ({ page }) => {
  await page.route('**/api/admin/session', route => route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"AUTH_UNAVAILABLE"}' }))
  await page.goto('/admin/login')
  await expect(page.getByRole('alert')).toContainText('登入服務暫時無法使用')
  await expect(page.getByRole('button', { name: '登入', exact: true })).toBeDisabled()
  await page.unroute('**/api/admin/session')
  await page.getByRole('button', { name: '重新載入驗證' }).click()
  await expect(page.getByRole('button', { name: '登入', exact: true })).toBeEnabled()
})

test('概覽讀取失敗不保留舊管理資料，重試回到真 API', async ({ page }) => {
  await signIn(page, 'ADMIN')
  await page.route('**/api/admin/overview', route => route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"AUTH_UNAVAILABLE"}' }))
  await page.reload()
  await expect(page.getByRole('alert')).toContainText('登入服務暫時無法使用')
  await expect(page.getByText('已公開作品：')).toHaveCount(0)
  await expect(page.getByTestId('security-summary')).toHaveCount(0)
  await page.unroute('**/api/admin/overview')
  await page.getByRole('button', { name: '重新載入', exact: true }).click()
  await expect(page.getByText('管理員', { exact: true })).toBeVisible()
})

test('320/390px 管理登入及概覽可使用，帳密不放在 storage', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 320, height: 850 })
  await signIn(page, 'ADMIN')
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 850 })
    await page.reload()
    await expect(page.getByRole('button', { name: '登出', exact: true })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`admin-${width}.png`), fullPage: true })
  }
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  await page.getByRole('button', { name: '登出', exact: true }).click()
  await expect(page).toHaveURL(/\/admin\/login$/)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
})

test('429 清空密碼、保留帳號與倒數，不自動重送或由重載驗證解除', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 850 })
  await page.goto('/admin/login')
  const account = credentials('ADMIN')
  await page.getByLabel('帳號', { exact: true }).fill(account.username)
  await page.getByLabel('密碼', { exact: true }).fill(account.password)
  let attempts = 0
  await page.route('**/api/admin/login', route => {
    attempts++
    return route.fulfill({ status: 429, contentType: 'application/json', headers: { 'Retry-After': '5' }, body: '{"code":"LOGIN_RATE_LIMITED"}' })
  })
  await page.getByRole('button', { name: '登入', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('登入嘗試過於頻繁，請稍後再試')
  await expect(page.getByLabel('密碼', { exact: true })).toHaveValue('')
  await expect(page.getByLabel('帳號', { exact: true })).toHaveValue(account.username)
  await expect(page.getByRole('button', { name: '登入', exact: true })).toBeDisabled()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('login-rate-390.png'), fullPage: true })
  await page.getByRole('button', { name: '重新載入驗證' }).click()
  await expect(page.getByRole('button', { name: '登入', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '登入', exact: true })).toBeEnabled({ timeout: 7000 })
  expect(attempts).toBe(1)
  await page.unroute('**/api/admin/login')
  await page.getByLabel('密碼', { exact: true }).fill(account.password)
  await page.getByRole('button', { name: '登入', exact: true }).click()
  await expect(page).toHaveURL(/\/admin$/)
})

test('到期身份回應清除管理概覽並要求手動重新登入', async ({ page, context }) => {
  await signIn(page, 'ADMIN')
  await expect(page.getByTestId('security-summary')).toContainText('8 小時')
  const session = await (await context.request.get('/api/admin/session')).json()
  // Explicit UI stub; real absolute expiry/revocation is tested by packaged runtime and PostgreSQL tests.
  await page.route('**/api/admin/session', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ ...session, authenticated: false, username: '', role: '' }) }))
  await page.reload()
  await expect(page).toHaveURL(/\/admin\/login$/)
  await expect(page.getByTestId('security-summary')).toHaveCount(0)
  await expect(page.getByText('已公開作品：')).toHaveCount(0)
  await expect(page.getByLabel('密碼', { exact: true })).toHaveValue('')
  expect((await context.request.post('/api/admin/logout', { headers: { 'X-CSRF-TOKEN': session.csrf.token } })).status()).toBe(204)
  await page.unroute('**/api/admin/session')
  await signIn(page, 'ADMIN')
})

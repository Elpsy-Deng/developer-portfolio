import { test, expect, type Page, type APIRequestContext } from '@playwright/test'

function credentials(role: 'ADMIN' | 'VIEWER') {
  const username = process.env[`PORTFOLIO_E2E_${role}_USERNAME`], password = process.env[`PORTFOLIO_E2E_${role}_PASSWORD`]
  if (!username || !password) throw new Error('Use own runtime private accounts')
  return { username, password }
}
async function signIn(page: Page, role: 'ADMIN' | 'VIEWER' = 'ADMIN') {
  await page.goto('/admin/login')
  await page.getByLabel('帳號', { exact: true }).fill(credentials(role).username)
  await page.getByLabel('密碼', { exact: true }).fill(credentials(role).password)
  await page.getByRole('button', { name: '登入', exact: true }).click()
  await expect(page).toHaveURL(/\/admin$/)
  await expect(page.getByRole('link', { name: '作品管理', exact: true })).toBeVisible()
}
async function csrf(api: APIRequestContext) { return (await (await api.get('/api/admin/session')).json()).csrf.token as string }
async function fixture(api: APIRequestContext, slug: string) {
  const options = await (await api.get('/api/admin/catalog-options')).json()
  const body = { content: { slug, title: 'Browser synthetic project', subtitle: 'Isolated evidence', category: options.categories[0], status: 'In Progress', statusNote: 'Test only', summary: 'Synthetic summary', description: 'Synthetic description', year: '2026', role: 'Developer', duration: 'Fixture', featured: false, theme: 'forest', technologyLabel: 'Existing tags', technologies: [options.technologies[0]], features: ['Initial feature'], challenges: [], architecture: [] }, published: false, sortOrder: null, expectedVersion: null }
  const response = await api.post('/api/admin/projects', { data: body, headers: { 'X-CSRF-TOKEN': await csrf(api) } })
  expect(response.status()).toBe(201)
  return response.json()
}
async function clean(api: APIRequestContext, slug: string) {
  const result = await api.get(`/api/admin/projects/${slug}`)
  if (result.status() === 404) return
  expect(result.status()).toBe(200)
  const value = await result.json()
  expect((await api.delete(`/api/admin/projects/${slug}`, { data: { expectedVersion: value.version }, headers: { 'X-CSRF-TOKEN': await csrf(api) } })).status()).toBe(204)
}
async function fillNew(page: Page, slug: string) {
  await page.goto('/admin/projects/new')
  await page.getByLabel('網址代號（建立後固定）').fill(slug)
  await page.getByLabel('作品名稱', { exact: true }).fill('UI synthetic project')
  await page.getByLabel('摘要', { exact: true }).fill('Synthetic UI summary')
  await page.getByLabel('作品說明', { exact: true }).fill('Synthetic UI description')
  await page.getByLabel('已完成功能（每行一項）').fill('First feature\nSecond feature')
}
test.beforeEach(({ page }) => { page.on('pageerror', e => { throw e }) })

test('管理 UI 新增草稿、編輯發布、下架與確認刪除，保留稽核', async ({ page, context }) => {
  const slug = 'browser-ui-lifecycle'
  await signIn(page)
  try {
    await fillNew(page, slug)
    await page.getByRole('button', { name: '新增來源證據', exact: true }).click()
    await page.getByLabel('證據名稱 1', { exact: true }).fill('Synthetic source')
    await page.getByLabel('HTTPS 來源連結 1', { exact: true }).fill('https://example.com/evidence')
    await page.getByLabel('來源核對日期').fill('2026-10-12')
    await page.getByRole('button', { name: '儲存作品', exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/admin/projects/${slug}$`))
    await expect(page.getByTestId('project-version')).toHaveText('目前版本 1')
    expect((await context.request.get(`/api/public/projects/${slug}`)).status()).toBe(404)
    await expect(page.getByLabel('已完成功能（每行一項）')).toHaveValue('First feature\nSecond feature')
    await page.getByLabel('發布到公開頁').check()
    await page.getByLabel('作品名稱', { exact: true }).fill('Published UI project')
    await page.getByRole('button', { name: '儲存作品', exact: true }).click()
    await expect(page.getByTestId('project-version')).toHaveText('目前版本 2')
    const publicDetail = await context.request.get(`/api/public/projects/${slug}`)
    expect(publicDetail.status()).toBe(200); expect((await publicDetail.json()).features).toEqual(['First feature', 'Second feature'])
    await page.reload()
    await expect(page.getByLabel('作品名稱', { exact: true })).toHaveValue('Published UI project')
    await page.getByLabel('發布到公開頁').uncheck()
    await page.getByRole('button', { name: '儲存作品', exact: true }).click()
    await expect(page.getByTestId('project-version')).toHaveText('目前版本 3')
    expect((await context.request.get(`/api/public/projects/${slug}`)).status()).toBe(404)
    await page.getByRole('button', { name: '刪除作品', exact: true }).click()
    expect((await context.request.get(`/api/admin/projects/${slug}`)).status()).toBe(200)
    await page.getByRole('button', { name: '確認刪除', exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`/admin/projects/${slug}/audit$`))
    await expect(page.locator('.admin-audit-row')).toHaveCount(4)
    await expect(page.locator('.admin-audit-row').first()).toContainText('刪除')
    const audit = await (await context.request.get(`/api/admin/projects/${slug}/audit`)).json()
    expect(audit[0].before.version).toBe(3); expect(audit[0].after).toBeNull()
  } finally { await clean(context.request, slug) }
})

test('兩個編輯頁的版本衝突保留輸入，明確核對最新資料', async ({ page, context }) => {
  const slug = 'browser-ui-conflict'
  await signIn(page); await fixture(context.request, slug)
  const other = await context.newPage()
  try {
    await page.goto(`/admin/projects/${slug}`); await other.goto(`/admin/projects/${slug}`)
    await expect(other.getByTestId('project-version')).toHaveText('目前版本 1')
    await page.getByLabel('作品名稱', { exact: true }).fill('First editor saves')
    await page.getByRole('button', { name: '儲存作品', exact: true }).click()
    await expect(page.getByTestId('project-version')).toHaveText('目前版本 2')
    await other.getByLabel('作品名稱', { exact: true }).fill('Unsaved second editor')
    await other.getByRole('button', { name: '儲存作品', exact: true }).click()
    await expect(other.getByRole('alert')).toContainText('作品已被更新')
    await expect(other.getByLabel('作品名稱', { exact: true })).toHaveValue('Unsaved second editor')
    await other.getByRole('button', { name: '載入最新版本' }).click()
    await expect(other.getByLabel('作品名稱', { exact: true })).toHaveValue('First editor saves')
    await expect(other.getByTestId('project-version')).toHaveText('目前版本 2')
    expect((await (await context.request.get(`/api/admin/projects/${slug}/audit`)).json()).length).toBe(2)
  } finally { await other.close(); await clean(context.request, slug) }
})

test('唯讀 UI 與真 API 都禁止作品寫入，無法直接進新增頁', async ({ page, context }) => {
  await signIn(page, 'VIEWER')
  await page.getByRole('link', { name: '作品管理', exact: true }).click()
  await expect(page.locator('.admin-project-list > li')).toHaveCount(5)
  await expect(page.getByRole('link', { name: '新增作品' })).toHaveCount(0)
  await page.goto('/admin/projects/developer-portfolio')
  await expect(page.getByLabel('作品名稱', { exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '儲存作品', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '刪除作品', exact: true })).toHaveCount(0)
  const version = (await (await context.request.get('/api/admin/projects/developer-portfolio')).json()).version
  const denied = await context.request.delete('/api/admin/projects/developer-portfolio', { data: { expectedVersion: version }, headers: { 'X-CSRF-TOKEN': await csrf(context.request) } })
  expect(denied.status()).toBe(403)
  await page.goto('/admin/projects/new')
  await expect(page.getByRole('alert')).toContainText('你沒有新增作品的權限')
  expect((await context.request.get('/api/public/projects/developer-portfolio')).status()).toBe(200)
})

test('作品列表失敗不留舊資料，儲存 503 保留表單並可手動重試', async ({ page, context }) => {
  const slug = 'browser-ui-failure'
  await signIn(page)
  try {
    await page.route('**/api/admin/projects?**', route => route.request().method() === 'GET' ? route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"CATALOG_UNAVAILABLE"}' }) : route.continue())
    await page.goto('/admin/projects')
    await expect(page.getByRole('alert')).toContainText('作品資料暫時無法讀取')
    await expect(page.locator('.admin-project-list > li')).toHaveCount(0)
    await page.unroute('**/api/admin/projects?**')
    await page.getByRole('button', { name: '重新載入', exact: true }).click()
    await expect(page.locator('.admin-project-list > li')).toHaveCount(5)
    await fillNew(page, slug)
    await page.route('**/api/admin/projects', route => route.request().method() === 'POST' ? route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"CATALOG_UNAVAILABLE"}' }) : route.continue())
    await page.getByRole('button', { name: '儲存作品', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('作品資料暫時無法讀取')
    await expect(page.getByLabel('作品名稱', { exact: true })).toHaveValue('UI synthetic project')
    expect((await context.request.get(`/api/admin/projects/${slug}`)).status()).toBe(404)
    await page.unroute('**/api/admin/projects')
    await page.getByRole('button', { name: '儲存作品', exact: true }).click()
    await expect(page.getByTestId('project-version')).toHaveText('目前版本 1')
  } finally { await page.unroute('**/api/admin/projects'); await clean(context.request, slug) }
})

test('320/390px 作品管理與完整編輯表單無橫向溢出', async ({ page }, testInfo) => {
  await signIn(page)
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 850 })
    await page.goto('/admin/projects')
    await expect(page.locator('.admin-project-list > li')).toHaveCount(5)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.goto('/admin/projects/developer-portfolio')
    await expect(page.getByLabel('作品名稱', { exact: true })).toHaveValue('Developer Portfolio')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    await page.screenshot({ path: testInfo.outputPath(`project-editor-${width}.png`), fullPage: true })
    await page.screenshot({ path: testInfo.outputPath(`project-editor-top-${width}.png`) })
  }
})

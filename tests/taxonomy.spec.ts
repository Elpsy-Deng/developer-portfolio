import { test, expect, type Page, type APIRequestContext } from '@playwright/test'
async function login(page: Page, role: 'ADMIN' | 'VIEWER' = 'ADMIN') {
  await page.goto('/admin/login')
  await page.getByLabel('帳號', { exact: true }).fill(process.env[`PORTFOLIO_E2E_${role}_USERNAME`]!)
  await page.getByLabel('密碼', { exact: true }).fill(process.env[`PORTFOLIO_E2E_${role}_PASSWORD`]!)
  await page.getByRole('button', { name: '登入', exact: true }).click()
  await expect(page).toHaveURL(/\/admin$/)
}
async function headers(api: APIRequestContext) { return { 'X-CSRF-TOKEN': (await (await api.get('/api/admin/session')).json()).csrf.token } }
async function terms(api: APIRequestContext, kind = 'categories') { return (await api.get(`/api/admin/taxonomy/${kind}`)).json() }
async function cleanup(api: APIRequestContext, slug: string | null, kind: string, id: string | undefined) {
  if (slug) { const r = await api.get(`/api/admin/projects/${slug}`); if (r.status() === 200) expect((await api.delete(`/api/admin/projects/${slug}`, { data: { expectedVersion: (await r.json()).version }, headers: await headers(api) })).status()).toBe(204) }
  if (id) { const t = (await terms(api, kind)).find((t: { id: string }) => t.id === id); if (t) expect((await api.delete(`/api/admin/taxonomy/${kind}/${id}`, { data: { expectedVersion: t.version }, headers: await headers(api) })).status()).toBe(204) }
}
test.beforeEach(({ page }) => { page.on('pageerror', e => { throw e }) })

test('動態分類 UI 新增與改名，舊分類及作品編輯頁衝突保留輸入，引用刪除拒絕', async ({ page, context }) => {
  const api = context.request, slug = 'browser-taxonomy-parent'; let id: string | undefined
  await login(page); await page.goto('/admin/taxonomy')
  await expect(page.getByRole('button', { name: '儲存項目', exact: true })).toBeEnabled()
  const other = await context.newPage(), projectEditor = await context.newPage()
  try {
    await page.getByLabel('項目名稱', { exact: true }).fill('Browser 新分類')
    await page.getByLabel('分類排序', { exact: true }).fill('100')
    await page.getByRole('button', { name: '儲存項目', exact: true }).click()
    await expect(page.getByRole('button', { name: '編輯 Browser 新分類', exact: true })).toBeVisible()
    id = (await terms(api)).find((t: { label: string }) => t.label === 'Browser 新分類').id
    const core = (await (await api.get('/api/admin/projects/developer-portfolio')).json()).project
    for (const k of ['coverImage', 'screenshots', 'localDemoUrl']) delete core[k]
    Object.assign(core, { slug, title: 'Browser taxonomy parent', category: 'Browser 新分類', featured: false })
    expect((await api.post('/api/admin/projects', { data: { content: core, published: true }, headers: await headers(api) })).status()).toBe(201)
    await projectEditor.goto(`/admin/projects/${slug}`); await expect(projectEditor.getByTestId('project-version')).toHaveText('目前版本 1')
    await other.goto('/admin/taxonomy'); await other.getByRole('button', { name: '編輯 Browser 新分類', exact: true }).click()
    await page.getByRole('button', { name: '編輯 Browser 新分類', exact: true }).click()
    await page.getByLabel('項目名稱', { exact: true }).fill('Browser 改名分類')
    await page.getByRole('button', { name: '儲存項目', exact: true }).click()
    await expect(page.getByRole('button', { name: '編輯 Browser 改名分類', exact: true })).toBeVisible()
    await other.getByLabel('項目名稱', { exact: true }).fill('Unsaved classification')
    await other.getByRole('button', { name: '儲存項目', exact: true }).click()
    await expect(other.getByRole('alert')).toContainText('分類或技術已更新')
    await expect(other.getByLabel('項目名稱', { exact: true })).toHaveValue('Unsaved classification')
    await other.getByRole('button', { name: '載入最新項目版本', exact: true }).click()
    await expect(other.getByLabel('項目名稱', { exact: true })).toHaveValue('Browser 改名分類')
    await other.getByLabel('確認刪除此項目').check(); await other.getByRole('button', { name: '刪除項目', exact: true }).click()
    await expect(other.getByRole('alert')).toContainText('仍被作品引用')
    await projectEditor.getByLabel('作品名稱', { exact: true }).fill('Unsaved project title')
    await projectEditor.getByRole('button', { name: '儲存作品', exact: true }).click()
    await expect(projectEditor.getByRole('alert')).toContainText('作品已被更新')
    await expect(projectEditor.getByLabel('作品名稱', { exact: true })).toHaveValue('Unsaved project title')
    await page.goto('/projects'); await page.getByRole('button', { name: 'Browser 改名分類', exact: true }).click()
    await expect(page.locator('.project-card h3')).toHaveText('Browser taxonomy parent')
    expect((await (await api.get(`/api/admin/projects/${slug}`)).json()).version).toBe(2)
  } finally { await other.close(); await projectEditor.close(); await cleanup(api, slug, 'categories', id) }
})

test('技術 UI 503 保留名稱，核對後改名及刪除留下稽核；390px無溢出', async ({ page, context }, info) => {
  const api = context.request; let id: string | undefined
  await login(page); await page.setViewportSize({ width: 390, height: 850 }); await page.goto('/admin/taxonomy')
  await page.getByRole('button', { name: '技術', exact: true }).click()
  try {
    await page.getByLabel('項目名稱', { exact: true }).fill('Browser unused tech')
    await page.getByRole('button', { name: '儲存項目', exact: true }).click()
    await expect(page.getByRole('button', { name: '編輯 Browser unused tech', exact: true })).toBeVisible()
    id = (await terms(api, 'technologies')).find((t: { label: string }) => t.label === 'Browser unused tech').id
    await page.getByRole('button', { name: '編輯 Browser unused tech', exact: true }).click()
    await page.getByLabel('項目名稱', { exact: true }).fill('Browser saved tech')
    await page.route('**/api/admin/taxonomy/technologies/*', r => r.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"CATALOG_UNAVAILABLE"}' }))
    await page.getByRole('button', { name: '儲存項目', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('作品資料暫時無法讀取')
    await expect(page.getByLabel('項目名稱', { exact: true })).toHaveValue('Browser saved tech')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: info.outputPath('taxonomy-390.png'), fullPage: true })
    await page.unroute('**/api/admin/taxonomy/technologies/*')
    await page.getByRole('button', { name: '重新載入項目', exact: true }).click()
    await expect(page.getByRole('button', { name: '儲存項目', exact: true })).toBeEnabled()
    await page.getByRole('button', { name: '儲存項目', exact: true }).click()
    await expect(page.getByRole('button', { name: '編輯 Browser saved tech', exact: true })).toBeVisible()
    await page.getByRole('button', { name: '編輯 Browser saved tech', exact: true }).click(); await page.getByLabel('確認刪除此項目').check()
    await page.getByRole('button', { name: '刪除項目', exact: true }).click()
    await expect(page.getByRole('button', { name: '編輯 Browser saved tech', exact: true })).toHaveCount(0)
    const audit = await (await api.get('/api/admin/taxonomy/audit')).json()
    expect(audit.filter((t: { termId: string }) => t.termId === id)).toHaveLength(3)
  } finally { await page.unroute('**/api/admin/taxonomy/technologies/*'); await cleanup(api, null, 'technologies', id) }
})

test('公開及管理分頁筆數／排序／深連結，搜尋重設頁碼且慢管理回應不覆蓋新條件', async ({ page }) => {
  await page.goto('/projects?page=2&pageSize=2')
  await expect(page.locator('.project-card')).toHaveCount(2)
  await expect(page.getByRole('navigation', { name: '分頁控制' })).toContainText('第 2 頁 · 共 3 頁／5 筆')
  const titles = await page.locator('.project-card h3').allTextContents(); await page.reload()
  await expect(page.locator('.project-card')).toHaveCount(2); expect(await page.locator('.project-card h3').allTextContents()).toEqual(titles)
  await page.getByRole('button', { name: '下一頁', exact: true }).click()
  await expect(page.locator('.project-card')).toHaveCount(1); await expect(page.getByRole('button', { name: '下一頁', exact: true })).toBeDisabled()
  await page.getByRole('searchbox', { name: '搜尋專案或技術' }).fill('Flutter')
  await expect(page.locator('.project-card h3')).toHaveText('ELPSY Shop'); await expect(page).toHaveURL(/page=1/)
  await login(page); await page.goto('/admin/projects'); await page.getByLabel('每頁筆數').selectOption('2')
  await expect(page.locator('.admin-project-list > li')).toHaveCount(2)
  await page.getByRole('button', { name: '下一頁', exact: true }).click(); await expect(page.getByRole('navigation', { name: '分頁控制' })).toContainText('第 2 頁')
  let release!: () => void, seen!: () => void, finished!: () => void
  const held = new Promise<void>(r => { release = r }), captured = new Promise<void>(r => { seen = r }), delivered = new Promise<void>(r => { finished = r })
  await page.route('**/api/admin/projects?**', async route => { if (new URL(route.request().url()).searchParams.get('q') === 'Flutter') { const r = await route.fetch(); seen(); await held; try { await route.fulfill({ response: r }) } finally { finished() } } else await route.continue() })
  await page.getByLabel('搜尋管理作品').fill('Flutter'); await captured
  await page.getByLabel('搜尋管理作品').fill('Qwen3'); await expect(page.locator('.admin-project-list h2')).toHaveText('Atlas · AI Knowledge Assistant')
  release(); await delivered; await page.evaluate(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))))
  await expect(page.locator('.admin-project-list h2')).toHaveText('Atlas · AI Knowledge Assistant')
  await expect(page.getByRole('navigation', { name: '分頁控制' })).toContainText('第 1 頁 · 共 1 頁／1 筆')
})

test('VIEWER可核對分類與技術稽核，UI無寫入入口且真API拒絕', async ({ page, context }) => {
  await login(page, 'VIEWER'); await page.goto('/admin/taxonomy')
  await expect(page.getByText('唯讀檢視者可核對分類、技術及稽核，不能修改。', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '儲存項目', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '技術', exact: true }).click(); await expect(page.locator('.admin-project-list > li')).toHaveCount(32)
  expect((await context.request.get('/api/admin/taxonomy/audit')).status()).toBe(200)
  expect((await context.request.post('/api/admin/taxonomy/technologies', { data: { label: 'Forbidden' }, headers: await headers(context.request) })).status()).toBe(403)
})

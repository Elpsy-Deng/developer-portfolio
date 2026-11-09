import { test, expect, type Page, type APIRequestContext } from '@playwright/test'
function account(role = 'ADMIN') {
  const username = process.env[`PORTFOLIO_E2E_${role}_USERNAME`], password = process.env[`PORTFOLIO_E2E_${role}_PASSWORD`]
  if (!username || !password) throw new Error('Private runtime accounts required')
  return { username, password }
}
function fixturePath() { if (!process.env.PORTFOLIO_E2E_IMAGE_FILE) throw new Error('Synthetic runtime PNG required'); return process.env.PORTFOLIO_E2E_IMAGE_FILE }
async function login(page: Page, role = 'ADMIN') {
  await page.goto('/admin/login');await page.getByLabel('帳號', { exact: true }).fill(account(role).username);await page.getByLabel('密碼', { exact: true }).fill(account(role).password);await page.getByRole('button', { name: '登入', exact: true }).click();await expect(page).toHaveURL(/\/admin$/)
}
async function csrf(api: APIRequestContext) {
  const response = await api.get('/api/admin/session')
  if (response.status() !== 200) test.info().annotations.push({ type: 'session-http-status', description: String(response.status()) })
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(typeof body.csrf?.token).toBe('string')
  return body.csrf.token as string
}
async function create(api: APIRequestContext, slug: string) {
  const content = { slug, title: 'Synthetic media UI', subtitle: 'Own fixture', category: '全端應用', status: 'In Progress', statusNote: '', summary: 'Own image evidence', description: 'Isolated synthetic images', year: '2026', role: 'Developer', duration: '', featured: false, theme: 'forest', technologyLabel: '', technologies: [], features: [], challenges: [], architecture: [] }
  const result = await api.post('/api/admin/projects', { data: { content, published: false }, headers: { 'X-CSRF-TOKEN': await csrf(api) } });expect(result.status()).toBe(201)
}
async function remove(api: APIRequestContext, slug: string) {
  const r = await api.get(`/api/admin/projects/${slug}`)
  if (r.status() === 200) expect((await api.delete(`/api/admin/projects/${slug}`, { data: { expectedVersion: (await r.json()).version }, headers: { 'X-CSRF-TOKEN': await csrf(api) } })).status()).toBe(204)
  expect((await api.post('/api/admin/media/cleanup', { headers: { 'X-CSRF-TOKEN': await csrf(api) } })).status()).toBe(200)
}
async function fields(page: Page) {
  await page.getByLabel('圖片檔案', { exact: true }).setInputFiles(fixturePath())
  await page.getByLabel('圖片標題', { exact: true }).fill('Own UI screenshot')
  await page.getByLabel('圖片說明', { exact: true }).fill('Synthetic upload')
  await page.getByLabel('替代文字', { exact: true }).fill('Synthetic green frame')
}
test.beforeEach(({ page }) => { page.on('pageerror', e => { throw e }) })

test('UI 上傳草稿截圖、修改說明、發布核對真圖、移除與回收', async ({ page, context }) => {
  const slug = 'browser-media-lifecycle';await login(page);await create(context.request, slug)
  try {
    await page.goto(`/admin/projects/${slug}/media`);await fields(page);await page.getByRole('button', { name: '上傳圖片', exact: true }).click()
    await expect(page.getByRole('status')).toHaveText('圖片已上傳');await expect(page.getByTestId('media-version')).toContainText('版本 2')
    const item = page.locator('.admin-media-list > li').first();await expect(item.locator('img')).toHaveJSProperty('naturalWidth', 20)
    let data = await (await context.request.get(`/api/admin/projects/${slug}/media`)).json();const src = data[0].src as string
    expect((await context.request.get(src)).status()).toBe(404)
    await item.getByLabel('媒體替代文字', { exact: true }).fill('Updated screenshot alt')
    await item.getByRole('button', { name: '儲存圖片說明', exact: true }).click();await expect(page.getByTestId('media-version')).toContainText('版本 3')
    await page.goto(`/admin/projects/${slug}`);await page.getByLabel('發布到公開頁').check();await page.getByRole('button', { name: '儲存作品', exact: true }).click();await expect(page.getByTestId('project-version')).toHaveText('目前版本 4')
    await page.goto(`/projects/${slug}`);await expect(page.locator('.media-screenshot img')).toHaveJSProperty('naturalWidth', 20);expect((await context.request.get(src)).status()).toBe(200)
    await page.goto(`/admin/projects/${slug}/media`);await page.locator('.admin-media-list > li').getByRole('button', { name: '移除圖片', exact: true }).click();await page.getByRole('button', { name: '確認移除圖片', exact: true }).click()
    await expect(page.locator('.admin-media-list > li')).toHaveCount(0);expect((await context.request.get(src)).status()).toBe(404)
    await page.getByRole('button', { name: '回收未引用素材', exact: true }).click();await expect(page.getByRole('status')).toContainText('回收完成')
    const audit = await (await context.request.get(`/api/admin/projects/${slug}/audit`)).json();expect(audit.length).toBe(5);expect(audit[0].before.project.screenshots.length).toBe(1);expect(audit[0].after.project.screenshots.length).toBe(0)
    data = await (await context.request.get(`/api/admin/projects/${slug}/media`)).json();expect(data.length).toBe(0)
  } finally { await remove(context.request, slug) }
})

test('圖片兩頁版本衝突保留說明，重載後不覆寫他人修改', async ({ page, context }) => {
  const slug = 'browser-media-conflict';await login(page);await create(context.request, slug);const other = await context.newPage()
  try {
    await page.goto(`/admin/projects/${slug}/media`);await fields(page);await page.getByRole('button', { name: '上傳圖片', exact: true }).click();await expect(page.getByTestId('media-version')).toContainText('版本 2')
    await other.goto(`/admin/projects/${slug}/media`);await expect(other.locator('.admin-media-list > li')).toHaveCount(1)
    await page.getByLabel('媒體標題', { exact: true }).fill('First caption');await page.getByRole('button', { name: '儲存圖片說明', exact: true }).click();await expect(page.getByTestId('media-version')).toContainText('版本 3')
    await other.getByLabel('媒體標題', { exact: true }).fill('Unsaved caption');await other.getByRole('button', { name: '儲存圖片說明', exact: true }).click();await expect(other.getByRole('alert')).toContainText('作品已被更新');await expect(other.getByLabel('媒體標題', { exact: true })).toHaveValue('Unsaved caption')
    await other.getByRole('button', { name: '重新載入圖片' }).click();await expect(other.getByLabel('媒體標題', { exact: true })).toHaveValue('First caption')
  } finally { await other.close();await remove(context.request, slug) }
})

test('偽造 PNG 被真服務拒絕，上傳 503 不假稱成功並保留輸入', async ({ page, context }) => {
  const slug = 'browser-media-failure';await login(page);await create(context.request, slug)
  try {
    await page.goto(`/admin/projects/${slug}/media`);await fields(page)
    await page.getByLabel('圖片檔案', { exact: true }).setInputFiles({ name: 'fake.png', mimeType: 'image/png', buffer: Buffer.from('<svg onload="bad"/>') })
    await page.getByRole('button', { name: '上傳圖片', exact: true }).click();await expect(page.getByRole('alert')).toContainText('圖片內容或尺寸不正確');await expect(page.locator('.admin-media-list > li')).toHaveCount(0)
    await fields(page)
    await page.route(`**/api/admin/projects/${slug}/media`, route => route.request().method() === 'POST' ? route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"IMAGE_STORAGE_UNAVAILABLE"}' }) : route.continue())
    await page.getByRole('button', { name: '上傳圖片', exact: true }).click();await expect(page.getByRole('alert')).toContainText('圖片儲存暫時無法使用');await expect(page.getByLabel('圖片標題', { exact: true })).toHaveValue('Own UI screenshot')
    await page.unroute(`**/api/admin/projects/${slug}/media`);await page.getByRole('button', { name: '上傳圖片', exact: true }).click();await expect(page.getByRole('status')).toHaveText('圖片已上傳')
  } finally { await page.unroute(`**/api/admin/projects/${slug}/media`);await remove(context.request, slug) }
})

test('唯讀圖片頁不提供上傳、修改、移除或回收，真 API 403', async ({ page, context }) => {
  await login(page, 'VIEWER');await page.goto('/admin/projects/developer-portfolio/media')
  await expect(page.getByText('唯讀身份可以預覽圖片，不能上傳、修改或移除。')).toBeVisible();await expect(page.getByRole('button', { name: '上傳圖片', exact: true })).toHaveCount(0);await expect(page.getByRole('button', { name: '回收未引用素材', exact: true })).toHaveCount(0)
  await expect(page.locator('.admin-media-list input').first()).toBeDisabled();expect((await context.request.post('/api/admin/media/cleanup', { headers: { 'X-CSRF-TOKEN': await csrf(context.request) } })).status()).toBe(403)
})

test('320/390px 真封面預覽與上傳表單無橫向溢出', async ({ page, context }, testInfo) => {
  const slug = 'browser-media-mobile';await login(page);await create(context.request, slug)
  try {
    await page.goto(`/admin/projects/${slug}/media`);await fields(page);await page.getByLabel('圖片用途').selectOption('COVER');await page.getByRole('button', { name: '上傳圖片', exact: true }).click();await expect(page.getByTestId('media-version')).toContainText('版本 2')
    for (const width of [320, 390]) {
      await page.setViewportSize({ width, height: 850 });await page.reload();await expect(page.locator('.admin-media-list img')).toHaveJSProperty('naturalWidth', 20)
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);await page.locator('.admin-media-list').scrollIntoViewIfNeeded();await page.screenshot({ path: testInfo.outputPath(`media-${width}.png`) })
    }
  } finally { await remove(context.request, slug) }
})

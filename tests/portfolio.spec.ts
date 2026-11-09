import { expect, test, type Page } from '@playwright/test'

async function expectNoDeployMarker(page: Page) {
  // Deployment verification markers must never ship on the public home page.
  await expect(page.getByText(/CD-VERIFY-/)).toHaveCount(0)
}

test.beforeEach(async ({ page }) => {
  page.on('pageerror', error => { throw error })
})

test('首頁展示四個主要作品，保留平台入口', async ({ page }, info) => {
  await page.goto('/')
  await expect(page.locator('.project-card')).toHaveCount(4)
  for (const name of ['發呆亭', 'ELPSY Shop', 'Encore · 售票平台', 'Atlas · AI Knowledge Assistant']) {
    await expect(page.locator('.project-card h3', { hasText: name })).toBeVisible()
  }
  await expect(page.locator('.project-card', { hasText: '日日選物' })).toHaveCount(0)
  await expectNoDeployMarker(page)
  await page.screenshot({ path: info.outputPath('home-desktop.png'), fullPage: true })
})

test('列表搜尋、分類及清除篩選能找到正確作品', async ({ page }) => {
  await page.goto('/projects')
  await expect(page.locator('.project-card')).toHaveCount(5)
  const search = page.getByRole('searchbox', { name: '搜尋專案或技術' })
  await search.fill('Flutter')
  await expect(page.locator('.project-card')).toHaveCount(1)
  await expect(page.locator('.project-card h3')).toHaveText('ELPSY Shop')
  await search.fill('露營') // Search the subtitle as well as technology/title.
  await expect(page.locator('.project-card h3')).toHaveText('發呆亭')
  await search.fill('')
  await page.getByRole('button', { name: '後端工程', exact: true }).click()
  await expect(page.locator('.project-card h3')).toHaveText('Encore · 售票平台')
  await search.fill('找不到的作品')
  await expect(page.getByRole('heading', { name: '目前沒有符合的專案' })).toBeVisible()
  await page.getByRole('button', { name: '清除篩選' }).click()
  await expect(page.locator('.project-card')).toHaveCount(5)
  await expect(page.getByText('RBAC / Authorization Demo', { exact: true })).toHaveCount(0)
  await expect(page.getByText('Python Utilities', { exact: true })).toHaveCount(0)
})

for (const [slug, title, limitation] of [
  ['fadaiting-camp', '發呆亭', '180 天保留'],
  ['elpsy-shop', 'ELPSY Shop', 'CI 重建 APK'],
  ['ticket-platform', 'Encore · 售票平台', 'DIRTY gate'],
  ['ai-knowledge-assistant', 'Atlas · AI Knowledge Assistant', '誤拒答'],
] as const) {
  test(`${title} 深連結呈現範圍、限制與固定版本證據`, async ({ page }) => {
    await page.goto(`/projects/${slug}`)
    await expect(page.locator('h1')).toHaveText(`${title}.`)
    await page.reload()
    await expect(page.locator('#implementation-scope li').first()).toBeVisible()
    await expect(page.locator('#limitations')).toContainText(limitation)
    await expect(page.locator('#evidence')).toContainText('2026-10-12')
    const evidence = page.locator('#evidence a')
    expect(await evidence.count()).toBeGreaterThanOrEqual(3)
    for (const link of await evidence.all()) {
      expect(await link.getAttribute('href')).toMatch(/^https:\/\/github\.com\/Elpsy-Deng\/[^/]+\/blob\/[0-9a-f]{7,40}\//)
      expect(await link.getAttribute('rel')).toBe('noopener noreferrer')
    }
    await expect(page.locator('.project-links')).toContainText('GitHub 存取權')
    await expect(page.getByRole('link', { name: 'Live Demo（在新分頁開啟）' })).toHaveCount(0)
  })
}

test('Atlas 使用真實 RAG 截圖，平台頁說明 API 與資料庫邊界', async ({ page }) => {
  await page.goto('/projects/ai-knowledge-assistant')
  await expect(page.locator('#implementation-scope')).toContainText('Qwen3 4B')
  await expect(page.locator('#verification')).toContainText('37/40')
  await expect(page.locator('#verification')).toContainText('不是獨立語意準確率')
  const images = page.locator('.detail-cover img, .media-screenshot img')
  expect(await images.count()).toBeGreaterThanOrEqual(3)
  for (const image of await images.all()) {
    await image.scrollIntoViewIfNeeded()
    await expect(image).toHaveJSProperty('complete', true)
    expect(await image.evaluate((element: HTMLImageElement) => element.naturalWidth)).toBeGreaterThan(0)
  }
  await page.goto('/projects/developer-portfolio')
  await expect(page.locator('#limitations')).toContainText('storage adapter')
  await expect(page.locator('#implementation-scope')).toContainText('搜尋 API')
  await expect(page.locator('#planned-scope')).toContainText('密碼重設')
  await expect(page.locator('#implementation-scope')).toContainText('PostgreSQL 原子／有界登入配額')
  await expect(page.locator('#limitations')).toContainText('帳號治理與最後管理員保護已加入')
})

test('列表 API 503 顯示錯誤而非空態，重試恢復真實資料', async ({ page }) => {
  await page.route('**/api/public/projects?**', route => route.fulfill({
    status: 503, contentType: 'application/json', body: '{"code":"UNAVAILABLE"}',
  }))
  await page.goto('/projects')
  await expect(page.getByRole('alert')).toContainText('作品暫時無法載入')
  await expect(page.locator('.project-card')).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '目前沒有符合的專案' })).toHaveCount(0)
  await page.unroute('**/api/public/projects?**')
  await page.getByRole('button', { name: '重新載入作品' }).click()
  await expect(page.locator('.project-card')).toHaveCount(5)
})

test('詳情 503 不冒充找不到，重試能恢復', async ({ page }) => {
  const endpoint = '**/api/public/projects/ticket-platform'
  await page.route(endpoint, route => route.fulfill({ status: 503, body: '{}' }))
  await page.goto('/projects/ticket-platform')
  await expect(page.getByRole('alert')).toBeVisible()
  await expect(page.getByRole('heading', { name: '找不到這個專案' })).toHaveCount(0)
  await page.unroute(endpoint)
  await page.getByRole('button', { name: '重新載入作品' }).click()
  await expect(page.locator('h1')).toHaveText('Encore · 售票平台.')
})

test('不完整成功回應被拒絕，不展示假的空清單', async ({ page }) => {
  await page.route('**/api/public/projects?**', route => route.fulfill({
    contentType: 'application/json', body: '{"items":[]}',
  }))
  await page.goto('/projects')
  await expect(page.getByRole('alert')).toContainText('格式不完整')
  await expect(page.locator('.empty-state')).toHaveCount(0)
})

test('慢搜尋回應不能覆蓋後來的條件', async ({ page }) => {
  let release!: () => void
  let captured!: () => void
  let finished!: () => void
  const held = new Promise<void>(resolve => { release = resolve })
  const seen = new Promise<void>(resolve => { captured = resolve })
  const delivered = new Promise<void>(resolve => { finished = resolve })
  await page.route('**/api/public/projects?**', async route => {
    const url = new URL(route.request().url())
    if (url.searchParams.get('q') === 'Flutter') {
      const response = await route.fetch()
      captured()
      await held
      try { await route.fulfill({ response }) } finally { finished() }
    } else await route.continue()
  })
  await page.goto('/projects')
  await expect(page.locator('.project-card')).toHaveCount(5)
  const search = page.getByRole('searchbox', { name: '搜尋專案或技術' })
  await search.fill('Flutter')
  await seen
  await expect(page.getByRole('status', { name: '' }).filter({ hasText: '正在載入作品' })).toBeVisible()
  await search.fill('Qwen3')
  await expect(page.locator('.project-card h3')).toHaveText('Atlas · AI Knowledge Assistant')
  release()
  await delivered
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
  await expect(page.locator('.results-info')).toContainText('Qwen3')
  await expect(page.locator('.project-card h3')).toHaveText('Atlas · AI Knowledge Assistant')
})

test('320px／390px 手機頁面不橫向溢出', async ({ page }, info) => {
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 844 })
    for (const path of ['/', '/projects', '/projects/elpsy-shop', '/projects/ticket-platform', '/projects/fadaiting-camp', '/projects/ai-knowledge-assistant', '/projects/developer-portfolio', '/about']) {
      await page.goto(path)
      await expect(page.locator('h1')).toBeVisible()
      if (path === '/') {
        await expect(page.locator('.project-card')).toHaveCount(4)
        await expectNoDeployMarker(page)
      }
      const dimensions = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth }))
      expect(dimensions.scroll, `${width}px ${path}`).toBeLessThanOrEqual(dimensions.width + 1)
    }
  }
  await page.goto('/projects')
  await page.screenshot({ path: info.outputPath('projects-mobile.png'), fullPage: true })
})

test('未知路由與已移出作品顯示 404，能回到列表', async ({ page }) => {
  await page.goto('/projects/e-commerce-platform')
  await expect(page.getByRole('heading', { name: '找不到這個專案' })).toBeVisible()
  await page.getByRole('link', { name: '瀏覽所有專案' }).click()
  await expect(page.locator('.project-card')).toHaveCount(5)
  await page.goto('/unknown')
  await expect(page.getByRole('heading', { name: '這個頁面還不存在' })).toBeVisible()
})

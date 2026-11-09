import { expect, test } from '@playwright/test'

const slugs = ['fadaiting-camp', 'elpsy-shop', 'ticket-platform', 'ai-knowledge-assistant', 'developer-portfolio']

test('匿名Session 429明確顯示並等待手動重新載入（UI故障stub）', async ({ page }) => {
  await page.route('**/api/admin/session', route => route.fulfill({ status: 429, headers: { 'Retry-After': '60' },
    json: { code: 'SESSION_RATE_LIMITED' } }))
  await page.goto('/admin/login')
  await expect(page.getByRole('alert')).toContainText('工作階段建立過於頻繁')
  await expect(page.getByRole('button', { name: '重新載入驗證' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '登入', exact: true })).toBeDisabled()
})

test('五作品使用真PG更新內容、固定來源與實際上傳架構圖', async ({ page }) => {
  for (const slug of slugs) {
    await page.goto(`/projects/${slug}`)
    await expect(page.locator('.challenge-card')).toHaveCount(3)
    await expect(page.locator('.challenge-card').first()).toContainText('情境：')
    await expect(page.locator('#evidence')).toContainText('2026-10-13')
    const figure = page.locator('.media-screenshot').filter({ hasText: '架構說明圖' })
    await expect(figure).toHaveCount(1)
    await expect(figure.locator('.placeholder-label')).toContainText('ARCHITECTURE ILLUSTRATION')
    await expect(figure).toContainText('非來源應用操作截圖')
    await figure.scrollIntoViewIfNeeded()
    await expect(figure.locator('img')).toHaveJSProperty('complete', true)
    expect(await figure.locator('img').evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(500)
    expect(await figure.locator('img').getAttribute('src')).toMatch(/^\/api\/public\/media\//)
  }
})

test('平台呈現Docker備份CI，來源案例保留mock／DIRTY／品質限制', async ({ page }) => {
  await page.goto('/projects/developer-portfolio')
  await expect(page.locator('#implementation-scope')).toContainText('GitHub Hosted CI')
  await expect(page.locator('#verification')).toContainText('105')
  await expect(page.locator('#evidence a').filter({ hasText: '實際GitHub Hosted CI' })).toHaveAttribute('href', 'https://github.com/Elpsy-Deng/developer-portfolio/actions/runs/38351233282')
  await expect(page.locator('#planned-scope')).toContainText('公開HTTPS')
  await page.goto('/projects/elpsy-shop')
  await expect(page.locator('#limitations')).toContainText('mock')
  await page.goto('/projects/ticket-platform')
  await expect(page.locator('#limitations')).toContainText('DIRTY')
  await page.goto('/projects/ai-knowledge-assistant')
  await expect(page.locator('.challenge-card')).toContainText(['情境：文件沒有權限', '情境：問法不同', '37/40不是可泛化準確率'])
})

test('個人資料暫不公開，工具箱標示程式來源而非任職經歷', async ({ page }) => {
  await page.goto('/about')
  await expect(page.getByText('個人姓名、聯絡方式與真實經歷暫不公開。', { exact: false })).toBeVisible()
  await expect(page.locator('.skills-grid')).toContainText('作品原始碼涵蓋')
  await expect(page.locator('.skills-grid')).not.toContainText('Oracle')
  await expect(page.locator('.profile-location')).not.toContainText('Taiwan')
})

test('320／390px案例與架構圖不溢出，保存實際平台合成展示截圖', async ({ page }, info) => {
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 850 })
    for (const slug of slugs) {
      await page.goto(`/projects/${slug}`)
      await expect(page.locator('.challenge-card').first()).toBeVisible()
      const figure = page.locator('.media-screenshot').filter({ hasText: '架構說明圖' })
      await figure.scrollIntoViewIfNeeded()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(true)
      if (width === 390) await page.screenshot({ path: info.outputPath(`${slug}-platform-showcase-390.png`), fullPage: true })
    }
  }
})

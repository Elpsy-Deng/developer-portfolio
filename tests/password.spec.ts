import { test, expect, type Page, type APIRequestContext } from '@playwright/test'
function credentials(role: 'ADMIN' | 'VIEWER') {
  const username = process.env[`PORTFOLIO_E2E_${role}_USERNAME`], password = process.env[`PORTFOLIO_E2E_${role}_PASSWORD`]
  if (!username || !password) throw new Error('Use owned private runtime accounts')
  return { username, password }
}
async function signIn(page: Page, username = credentials('ADMIN').username, password = credentials('ADMIN').password) {
  await page.goto('/admin/login')
  await page.getByLabel('帳號', { exact: true }).fill(username)
  await page.getByLabel('密碼', { exact: true }).fill(password)
  await page.getByRole('button', { name: '登入', exact: true }).click()
  await expect(page).toHaveURL(/\/admin$/)
}
async function csrf(api: APIRequestContext) {
  const response = await api.get('/api/admin/session')
  if (response.status() !== 200) test.info().annotations.push({ type: 'session-http-status', description: String(response.status()) })
  expect(response.status()).toBe(200)
  return (await response.json()).csrf.token as string
}
async function fill(page: Page, current: string, next: string, confirm = next) {
  await page.getByLabel('目前密碼', { exact: true }).fill(current)
  await page.getByLabel('新密碼', { exact: true }).fill(next)
  await page.getByLabel('再次輸入新密碼', { exact: true }).fill(confirm)
}
async function cleared(page: Page) { for (const label of ['目前密碼', '新密碼', '再次輸入新密碼']) await expect(page.getByLabel(label, { exact: true })).toHaveValue('') }
test.beforeEach(({ page }) => { page.on('pageerror', e => { throw e }) })

test('本人新密碼真實驗證與全部裝置登出，VIEWER可以更改自己', async ({ page, context, browser }) => {
  await signIn(page)
  const username = 'browser_password_self', old = credentials('VIEWER').password, next = old + 'changed'
  expect((await context.request.post('/api/admin/accounts', { headers: { 'X-CSRF-TOKEN': await csrf(context.request) }, data: { username, password: old, role: 'VIEWER', enabled: true } })).status()).toBe(201)
  expect((await context.request.post('/api/admin/logout', { headers: { 'X-CSRF-TOKEN': await csrf(context.request) } })).status()).toBe(204)
  const peerContext = await browser.newContext(), peer = await peerContext.newPage()
  try {
    await signIn(page, username, old); await signIn(peer, username, old)
    await page.getByRole('link', { name: '更改我的密碼', exact: true }).click()
    await fill(page, old, next)
    await page.getByRole('button', { name: '更改密碼並登出所有裝置', exact: true }).click()
    await expect(page.getByRole('status')).toContainText('所有舊登入已撤銷')
    expect((await context.request.get('/api/admin/overview')).status()).toBe(401)
    expect((await peerContext.request.get('/api/admin/overview')).status()).toBe(401)
    await page.getByRole('link', { name: '使用新密碼登入', exact: true }).click()
    await page.getByLabel('帳號', { exact: true }).fill(username); await page.getByLabel('密碼', { exact: true }).fill(old)
    await page.getByRole('button', { name: '登入', exact: true }).click()
    await expect(page.getByRole('alert')).toHaveText('帳號或密碼不正確')
    await page.getByLabel('密碼', { exact: true }).fill(next); await page.getByRole('button', { name: '登入', exact: true }).click()
    await expect(page).toHaveURL(/\/admin$/)
    expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  } finally { await peerContext.close() }
})

test('錯誤現有密碼、重用與兩次不一致都清空秘密，不改身份', async ({ page, context }) => {
  await signIn(page); await page.goto('/admin/password')
  const original = await (await context.request.get('/api/admin/password')).json(), old = credentials('ADMIN').password
  await fill(page, 'wrong', old + 'next'); await page.getByRole('button', { name: '更改密碼並登出所有裝置', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('目前密碼不正確'); await cleared(page)
  await fill(page, old, old); await page.getByRole('button', { name: '更改密碼並登出所有裝置', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('新密碼必須與目前密碼不同'); await cleared(page)
  await fill(page, old, old + 'next', old + 'different'); await page.getByRole('button', { name: '更改密碼並登出所有裝置', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('兩次新密碼不一致'); await cleared(page)
  expect(await (await context.request.get('/api/admin/password')).json()).toEqual(original)
})

test('更改配額429倒數清空密碼、不自動重送或由身份重載解除', async ({ page }) => {
  await signIn(page); await page.goto('/admin/password')
  let attempts = 0
  await page.route('**/api/admin/password', route => {
    if (route.request().method() !== 'POST') return route.continue()
    attempts++
    return route.fulfill({ status: 429, contentType: 'application/json', headers: { 'Retry-After': '5' }, body: '{"code":"PASSWORD_RATE_LIMITED"}' })
  })
  await fill(page, credentials('ADMIN').password, credentials('ADMIN').password + 'next')
  await page.getByRole('button', { name: '更改密碼並登出所有裝置', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('密碼更改嘗試過於頻繁，請稍後再試'); await cleared(page)
  await page.getByRole('button', { name: '重新核對身份' }).click()
  await expect(page.getByRole('button', { name: '更改密碼並登出所有裝置', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: '更改密碼並登出所有裝置', exact: true })).toBeEnabled({ timeout: 7000 })
  expect(attempts).toBe(1)
})

test('503結果不確定時清空秘密且不重送，390px更改頁可用', async ({ page }, testInfo) => {
  await signIn(page); await page.setViewportSize({ width: 390, height: 850 }); await page.goto('/admin/password')
  let attempts = 0
  await page.route('**/api/admin/password', route => {
    if (route.request().method() !== 'POST') return route.continue()
    attempts++
    return route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"AUTH_UNAVAILABLE"}' })
  })
  await fill(page, credentials('ADMIN').password, credentials('ADMIN').password + 'next')
  await page.getByRole('button', { name: '更改密碼並登出所有裝置', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('結果尚未確認'); await cleared(page)
  expect(attempts).toBe(1)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: testInfo.outputPath('password-390.png'), fullPage: true })
})

import { defineConfig } from '@playwright/test'

const run = new Date().toISOString().replace(/[:.]/g, '-')
const output = process.env.PORTFOLIO_E2E_OUTPUT ?? `work/browser/${run}`
const port = Number(process.env.PORTFOLIO_E2E_PORT ?? 15186)
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid test port')
const origin = `http://127.0.0.1:${port}`
const deployed = process.env.PORTFOLIO_E2E_DEPLOYED_URL
if (deployed && deployed !== origin) throw new Error('Deployed browser tests require the explicit own loopback gateway')
const apiPort = Number(process.env.PORTFOLIO_PORT ?? 19186)
if (!Number.isInteger(apiPort) || apiPort < 1024 || apiPort > 65535 || apiPort === port) throw new Error('Invalid API port')
const api = `http://127.0.0.1:${apiPort}`

export default defineConfig({
  testDir: './tests',
  testMatch: process.env.PORTFOLIO_E2E_SHOWCASE === '1' ? '**/showcase.spec.ts' : '**/*.spec.ts',
  testIgnore: process.env.PORTFOLIO_E2E_SHOWCASE === '1' ? [] : ['**/showcase.spec.ts'],
  workers: 1,
  retries: 0,
  timeout: 30_000,
  outputDir: `${output}/results`,
  reporter: [['list'], ['json', { outputFile: `${output}/report.json` }]],
  use: {
    baseURL: origin,
    browserName: 'chromium',
    viewport: { width: 1440, height: 1000 },
    launchOptions: {
      ...(process.env.PORTFOLIO_CHROMIUM_PATH
        ? { executablePath: process.env.PORTFOLIO_CHROMIUM_PATH }
        : {}),
    },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  webServer: deployed ? undefined : [{
    command: 'npm run backend:start',
    url: `${api}/api/public/health`,
    env: { PORTFOLIO_PORT: String(apiPort) },
    reuseExistingServer: false,
    timeout: 60_000,
  }, {
    command: `npm run preview -- --host 127.0.0.1 --port ${port} --strictPort`,
    url: origin,
    env: { PORTFOLIO_API_URL: api },
    reuseExistingServer: false,
    timeout: 30_000,
  }],
})

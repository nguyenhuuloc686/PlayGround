import { mkdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { createRequire } from 'node:module'

const url = process.env.CAPTURE_URL
const output = process.env.CAPTURE_DIR
if (!url || !output) {
  console.error('Set CAPTURE_URL and CAPTURE_DIR.')
  process.exit(1)
}

const runtime = join(process.env.HOME || '', '.local/share/omgithub-playwright')
const require = createRequire(join(runtime, 'package.json'))
let chromium
try {
  chromium = require('playwright').chromium
} catch (error) {
  console.error(`Playwright unavailable: ${error.message}`)
  process.exit(75)
}
mkdirSync(output, { recursive: true })

let config = { browser: { launchOptions: {} } }
try {
  config = JSON.parse(readFileSync(join(runtime, 'linux.json'), 'utf8'))
} catch {}
if (process.platform === 'linux') {
  try {
    process.env.DISPLAY ||= ':' + readFileSync(join(runtime, 'display'), 'utf8').trim()
  } catch {}
}

const transient = error => Object.assign(error, { exitCode: 75 })

let browser
let hadTransient = false
try {
  browser = await chromium.launch({ ...config.browser.launchOptions, timeout: 30000 })
  for (const [name, width, height] of [['desktop', 1440, 900], ['mobile', 390, 844]]) {
    const t0 = Date.now()
    const page = await browser.newPage({ viewport: { width, height } }).catch(transient)
    page.setDefaultTimeout(30000)
    page.on('pageerror', error => console.error(error.message))
    const response = await page.goto(url, { waitUntil: 'load', timeout: 45000 }).catch(transient)
    if (response?.ok?.() === false) {
      const code = response.status()
      throw Object.assign(new Error(`HTTP ${code} loading preview`), {
        exitCode: [408, 429, 500, 502, 503, 504].includes(code) ? 75 : 1
      })
    }
    if (!response) throw transient(new Error('No response from navigation'))
    await page.locator('#game').waitFor({ state: 'visible', timeout: 30000 }).catch(transient)
    await page.waitForFunction(() => !!window.__game?.ready, null, { timeout: 30000 }).catch(transient)
    await page.waitForTimeout(800)
    await page.screenshot({ path: join(output, `final-${name}.png`), timeout: 30000 }).catch(transient)
    console.log(`capture ${name}: ${Math.round((Date.now() - t0) / 100) / 10}s`)
    await page.close()
  }
} catch (error) {
  console.error(error)
  hadTransient = true
  process.exitCode = error.exitCode || 1
} finally {
  try {
    await browser?.close({ reason: 'capture finished' })
  } catch (error) {
    console.error(error)
    process.exitCode ||= hadTransient ? 75 : 1
  }
}

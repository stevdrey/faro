// SPDX-License-Identifier: AGPL-3.0-or-later
import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import type {} from './harness.tsx'
const harness = '/tests/e2e/harness.html?activity=app'
const panel = (page: Page) => page.getByRole('region', { name: 'Contame cómo lo hiciste' })
const control = (page: Page, name: string) => panel(page).getByRole('button', { name, exact: true })

declare global {
  interface Window {
    captureMedia: {
      requests: MediaStreamConstraints[]
      tracks: { readyState: string; stops: number; dispatchEvent: (event: Event) => boolean }[]
      recorders: { starts: number; stops: number; finish: () => void; dispatchEvent: (event: Event) => boolean }[]
      permission: 'success' | 'pending' | 'denied' | 'device'
      resolve: () => void
      autoFinalize: boolean
      active: Set<HTMLMediaElement>
      plays: number
      created: string[]
      revoked: string[]
    }
  }
}
async function mockCapture(page: Page) {
  await page.addInitScript(() => {
    const media: Window['captureMedia'] = {
      requests: [], tracks: [], recorders: [], permission: 'success', resolve: () => {},
      autoFinalize: true, active: new Set(), plays: 0, created: [], revoked: [],
    }
    window.captureMedia = media
    class Track extends EventTarget {
      readyState = 'live'
      stops = 0
      stop() { this.stops++; this.readyState = 'ended' }
    }
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true, value: (constraints: MediaStreamConstraints) => {
      media.requests.push(constraints)
      if (media.permission === 'denied') return Promise.reject(new DOMException('Synthetic private detail', 'NotAllowedError'))
      if (media.permission === 'device') return Promise.reject(new DOMException('Synthetic private detail', 'NotReadableError'))
      const track = new Track()
      media.tracks.push(track)
      const stream = { getTracks: () => [track], getAudioTracks: () => [track] }
      if (media.permission === 'pending') return new Promise(resolve => { media.resolve = () => resolve(stream) })
      return Promise.resolve(stream)
    } })
    class Recorder extends EventTarget {
      state = 'inactive'
      mimeType = 'audio/webm'
      starts = 0
      stops = 0
      constructor() { super(); media.recorders.push(this) }
      start() { this.starts++; this.state = 'recording' }
      stop() { this.stops++; this.state = 'inactive'; if (media.autoFinalize) queueMicrotask(() => this.finish()) }
      finish() {
        this.dispatchEvent(new BlobEvent('dataavailable', { data: new Blob(['synthetic'], { type: this.mimeType }) }))
        this.dispatchEvent(new Event('stop'))
      }
    }
    Object.defineProperty(window, 'MediaRecorder', { configurable: true, value: Recorder })
    HTMLMediaElement.prototype.play = function () { media.plays++; media.active.add(this); return Promise.resolve() }
    HTMLMediaElement.prototype.pause = function () { media.active.delete(this) }
    HTMLMediaElement.prototype.load = function () {}
    Object.defineProperty(HTMLMediaElement.prototype, 'paused', { get() { return !media.active.has(this) } })
    const create = URL.createObjectURL.bind(URL)
    const revoke = URL.revokeObjectURL.bind(URL)
    URL.createObjectURL = blob => { const url = create(blob); media.created.push(url); return url }
    URL.revokeObjectURL = url => { media.revoked.push(url); revoke(url) }
  })
}
async function finishAndAuthorize(page: Page) {
  await page.getByRole('button', { name: 'Listo', exact: true }).click()
  await expect(panel(page)).toBeVisible()
  if (await control(page, 'Habilitar grabación').count()) {
    await panel(page).getByRole('checkbox').check()
    await control(page, 'Habilitar grabación').click()
  }
}
async function record(page: Page) {
  await control(page, 'Grabar explicación').click()
  await expect(control(page, 'Detener grabación')).toBeVisible()
}
async function clip(page: Page) {
  await record(page)
  await control(page, 'Detener grabación').click()
  await expect(control(page, 'Escuchar mi grabación')).toBeVisible()
}

for (const activity of [
  { name: 'Bloques', example: undefined, reset: 'Intentar de nuevo' },
  { name: 'Secuencias', example: 'descending', reset: 'Empezar de nuevo' },
  { name: 'Secuencias', example: 'ascending', reset: 'Empezar de nuevo' },
  { name: 'Restas', example: undefined, reset: 'Empezar de nuevo' },
]) {
  test(`${activity.name} ${activity.example ?? ''}: explicit consent, record, replay, replace and delete preserve the attempt`, async ({ page }) => {
    await mockCapture(page)
    await page.goto(harness)
    await page.getByRole('button', { name: activity.name, exact: true }).click()
    if (activity.example) await page.getByRole('combobox').selectOption(activity.example)
    await expect(panel(page)).toHaveCount(0)
    await page.getByRole('button', { name: 'Listo', exact: true }).click()
    await expect(control(page, 'Habilitar grabación')).toBeDisabled()
    expect(await page.evaluate(() => window.captureMedia.requests)).toEqual([])
    await panel(page).getByRole('checkbox').check()
    await control(page, 'Habilitar grabación').click()
    expect(await page.evaluate(() => window.captureMedia.requests)).toEqual([])
    const completed = await page.evaluate(() => window.attemptEvidence.current)
    await control(page, 'Grabar explicación').focus()
    await page.keyboard.press('Enter')
    await expect(control(page, 'Detener grabación')).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(control(page, 'Grabar de nuevo')).toBeFocused()
    await control(page, 'Escuchar mi grabación').click()
    expect(await page.evaluate(() => window.captureMedia.active.size)).toBe(1)
    await control(page, 'Detener reproducción').click()
    expect(await page.evaluate(() => window.captureMedia.active.size)).toBe(0)
    await control(page, 'Grabar de nuevo').click()
    await expect(control(page, 'Escuchar mi grabación')).toHaveCount(0)
    await control(page, 'Detener grabación').click()
    await control(page, 'Borrar').click()
    await expect(control(page, 'Grabar explicación')).toBeFocused()
    await expect(control(page, 'Escuchar mi grabación')).toHaveCount(0)
    expect(await page.evaluate(() => window.captureMedia.requests)).toEqual([
      { audio: true, video: false }, { audio: true, video: false },
    ])
    expect(await page.evaluate(() => window.captureMedia.tracks.every(track => track.readyState === 'ended'))).toBe(true)
    expect(await page.evaluate(() => window.captureMedia.created.every(url => window.captureMedia.revoked.includes(url)))).toBe(true)
    expect(await page.evaluate(() => window.attemptEvidence.current)).toEqual(completed)
    await page.getByRole('button', { name: activity.reset, exact: true }).click()
    await expect(panel(page)).toHaveCount(0)
    await finishAndAuthorize(page)
    await expect(control(page, 'Grabar explicación')).toBeVisible()
    await page.reload()
    await page.getByRole('button', { name: 'Listo', exact: true }).click()
    await expect(control(page, 'Habilitar grabación')).toBeDisabled()
  })
}

test('batched repeated start events cannot lose the capture lease or disable later guidance', async ({ page }) => {
  await mockCapture(page)
  await page.goto(harness)
  await finishAndAuthorize(page)
  await page.evaluate(() => { window.captureMedia.permission = 'pending' })
  await control(page, 'Grabar explicación').evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click() })
  expect(await page.evaluate(() => window.captureMedia.requests.length)).toBe(1)
  await control(page, 'Cancelar').click()
  await expect(control(page, 'Escuchar cómo grabar')).toHaveAttribute('aria-disabled', 'false')
  await page.evaluate(() => { window.captureMedia.resolve(); window.captureMedia.permission = 'success' })
  await record(page)
  await control(page, 'Cancelar').click()
  await expect(control(page, 'Escuchar cómo grabar')).toHaveAttribute('aria-disabled', 'false')
  expect(await page.evaluate(() => window.captureMedia.tracks.every(track => track.readyState === 'ended'))).toBe(true)
})

test('guidance and clip playback exclude one another; capture silences and blocks all guidance', async ({ page }) => {
  await mockCapture(page)
  await page.goto(harness)
  await finishAndAuthorize(page)
  await page.getByRole('button', { name: 'Escuchar guía', exact: true }).click()
  await control(page, 'Escuchar cómo grabar').click()
  expect(await page.evaluate(() => window.captureMedia.active.size)).toBe(1)
  await record(page)
  expect(await page.evaluate(() => window.captureMedia.active.size)).toBe(0)
  const plays = await page.evaluate(() => window.captureMedia.plays)
  await expect(control(page, 'Escuchar cómo grabar')).toHaveAttribute('aria-disabled', 'true')
  await control(page, 'Escuchar cómo grabar').dispatchEvent('click')
  await page.getByRole('button', { name: 'Escuchar guía', exact: true }).dispatchEvent('click')
  expect(await page.evaluate(() => window.captureMedia.plays)).toBe(plays)
  await control(page, 'Detener grabación').click()
  await control(page, 'Escuchar mi grabación').click()
  await control(page, 'Escuchar cómo grabar').click()
  expect(await page.evaluate(() => window.captureMedia.active.size)).toBe(1)
  await control(page, 'Escuchar mi grabación').click()
  await control(page, 'Grabar de nuevo').click()
  expect(await page.evaluate(() => window.captureMedia.active.size)).toBe(0)
  await control(page, 'Cancelar').click()
})

test('a media pause while clip playback is still starting is not reported as a failure', async ({ page }) => {
  await mockCapture(page)
  await page.goto(harness)
  await finishAndAuthorize(page)
  await clip(page)
  await page.evaluate(() => {
    HTMLMediaElement.prototype.play = function () {
      window.captureMedia.plays++
      window.captureMedia.active.add(this)
      return new Promise((_, reject) => {
        Object.assign(window, { rejectPlay: () => reject(new DOMException('Synthetic private detail', 'AbortError')) })
      })
    }
  })
  await control(page, 'Escuchar mi grabación').click()
  await expect(panel(page).getByText('Escuchando tu grabación…')).toBeVisible()
  await page.evaluate(() => {
    // A media key or lost headset pauses before play() settles; the browser then aborts play().
    const player = document.querySelector<HTMLAudioElement>('.recording-playback audio')!
    window.captureMedia.active.delete(player)
    player.dispatchEvent(new Event('pause'))
    ;(window as unknown as { rejectPlay: () => void }).rejectPlay()
  })
  await expect(control(page, 'Detener reproducción')).toHaveAttribute('aria-disabled', 'true')
  await expect(panel(page).getByText('No se pudo escuchar', { exact: false })).toHaveCount(0)
})

for (const phase of ['recording', 'playback']) for (const change of ['reset', 'activity', 'example', 'hidden', 'pagehide', 'unmount']) {
  test(`${change} during ${phase} discards capture/clip, stops playback and never resumes automatically`, async ({ page }) => {
    await mockCapture(page)
    await page.goto(harness)
    if (change === 'example') await page.getByRole('button', { name: 'Secuencias', exact: true }).click()
    await finishAndAuthorize(page)
    if (phase === 'recording') await record(page)
    else { await clip(page); await control(page, 'Escuchar mi grabación').click() }
    if (change === 'reset') await page.getByRole('button', { name: 'Intentar de nuevo', exact: true }).click()
    if (change === 'activity') await page.getByRole('button', { name: 'Restas', exact: true }).click()
    if (change === 'example') await page.getByRole('combobox').selectOption('ascending')
    if (change === 'hidden') await page.evaluate(() => {
      Object.defineProperty(document, 'hidden', { configurable: true, value: true })
      document.dispatchEvent(new Event('visibilitychange'))
    })
    if (change === 'pagehide') await page.evaluate(() => window.dispatchEvent(new Event('pagehide')))
    if (change === 'unmount') await page.evaluate(() => window.unmountHarness())
    expect(await page.evaluate(() => window.captureMedia.active.size)).toBe(0)
    await expect(control(page, 'Escuchar mi grabación')).toHaveCount(0)
    expect(await page.evaluate(() => window.captureMedia.created.every(url => window.captureMedia.revoked.includes(url)))).toBe(true)
    expect(await page.evaluate(() => window.captureMedia.tracks.every(track => track.readyState === 'ended'))).toBe(true)
    if (change === 'hidden' || change === 'pagehide') {
      await page.evaluate(() => {
        Object.defineProperty(document, 'hidden', { configurable: true, value: false })
        document.dispatchEvent(new Event('visibilitychange'))
      })
      await record(page)
      await page.evaluate(() => window.dispatchEvent(new Event('pagehide')))
      await expect(control(page, 'Grabar explicación')).toBeVisible()
      expect(await page.evaluate(() => window.captureMedia.requests.length)).toBe(2)
      expect(await page.evaluate(() => window.captureMedia.tracks.every(track => track.readyState === 'ended'))).toBe(true)
    }
  })
}

for (const action of ['cancel', 'reset', 'activity', 'example', 'hidden', 'pagehide', 'unmount']) {
  test(`late permission after ${action} is released without recording`, async ({ page }) => {
    await mockCapture(page)
    await page.goto(harness)
    if (action === 'example') await page.getByRole('button', { name: 'Secuencias', exact: true }).click()
    await finishAndAuthorize(page)
    await page.evaluate(() => { window.captureMedia.permission = 'pending' })
    await control(page, 'Grabar explicación').click()
    await expect(panel(page).getByText('Esperando permiso del micrófono…')).toBeVisible()
    await control(page, 'Grabar explicación').dispatchEvent('click')
    if (action === 'cancel') await control(page, 'Cancelar').click()
    if (action === 'reset') await page.getByRole('button', { name: 'Intentar de nuevo', exact: true }).click()
    if (action === 'activity') await page.getByRole('button', { name: 'Restas', exact: true }).click()
    if (action === 'example') await page.getByRole('combobox').selectOption('ascending')
    if (action === 'hidden') await page.evaluate(() => {
      Object.defineProperty(document, 'hidden', { configurable: true, value: true })
      document.dispatchEvent(new Event('visibilitychange'))
    })
    if (action === 'pagehide') await page.evaluate(() => window.dispatchEvent(new Event('pagehide')))
    if (action === 'unmount') await page.evaluate(() => window.unmountHarness())
    await page.evaluate(() => window.captureMedia.resolve())
    expect(await page.evaluate(() => window.captureMedia.requests.length)).toBe(1)
    expect(await page.evaluate(() => window.captureMedia.recorders.length)).toBe(0)
    expect(await page.evaluate(() => window.captureMedia.tracks.every(track => track.readyState === 'ended'))).toBe(true)
  })
}

test('reselecting preserves a clip; changing activities clears it but keeps page authorization', async ({ page }) => {
  await mockCapture(page)
  await page.goto(harness)
  await finishAndAuthorize(page)
  await clip(page)
  await page.getByRole('button', { name: 'Bloques', exact: true }).click()
  await expect(control(page, 'Escuchar mi grabación')).toBeVisible()
  await page.getByRole('button', { name: 'Restas', exact: true }).click()
  await expect(panel(page)).toHaveCount(0)
  await page.getByRole('button', { name: 'Listo', exact: true }).click()
  await expect(control(page, 'Grabar explicación')).toBeVisible()
  await expect(control(page, 'Habilitar grabación')).toHaveCount(0)
  expect(await page.evaluate(() => window.captureMedia.requests.length)).toBe(1)
})

test('duration limit finalizes once, and cancel can discard queued final data', async ({ page }) => {
  await mockCapture(page)
  await page.clock.install()
  await page.goto(harness)
  await finishAndAuthorize(page)
  await record(page)
  await page.clock.runFor(30_000)
  await expect(control(page, 'Escuchar mi grabación')).toBeVisible()
  await expect(panel(page).getByLabel('Tiempo de grabación')).toHaveText('30 / 30 s')
  expect(await page.evaluate(() => window.captureMedia.recorders[0].stops)).toBe(1)
  await page.evaluate(() => { window.captureMedia.autoFinalize = false })
  await control(page, 'Grabar de nuevo').click()
  await control(page, 'Detener grabación').click()
  await expect(panel(page).getByText('Terminando la grabación…')).toBeVisible()
  await control(page, 'Cancelar').click()
  await page.evaluate(() => window.captureMedia.recorders[1].finish())
  await expect(control(page, 'Escuchar mi grabación')).toHaveCount(0)
})

for (const error of ['denied', 'device', 'unsupported', 'interrupted', 'recorder'] as const) {
  test(`${error} shows neutral recovery without retaining private data`, async ({ page }) => {
    await mockCapture(page)
    await page.goto(harness)
    await finishAndAuthorize(page)
    if (error === 'denied' || error === 'device') await page.evaluate(value => { window.captureMedia.permission = value }, error)
    if (error === 'unsupported') await page.evaluate(() => { Object.defineProperty(window, 'MediaRecorder', { value: undefined }) })
    await control(page, 'Grabar explicación').click()
    if (error === 'interrupted') await page.evaluate(() => window.captureMedia.tracks[0].dispatchEvent(new Event('ended')))
    if (error === 'recorder') await page.evaluate(() => window.captureMedia.recorders[0].dispatchEvent(new Event('error')))
    await expect(panel(page).locator('.recording-indicator')).toContainText(/Podés|Pedile/)
    await expect(control(page, 'Escuchar mi grabación')).toHaveCount(0)
    await expect(page.getByText('Synthetic private detail', { exact: false })).toHaveCount(0)
    await page.getByRole('button', { name: 'Intentar de nuevo', exact: true }).click()
    await expect(page.getByRole('button', { name: 'Listo', exact: true })).toBeVisible()
  })
}

test.describe('phone recording', () => {
  test.use({ hasTouch: true, viewport: { width: 360, height: 1000 } })
  test('touch controls fit, are at least 48px and support record/cancel/replay/delete', async ({ page }) => {
    await mockCapture(page)
    await page.goto(harness)
    await finishAndAuthorize(page)
    await control(page, 'Grabar explicación').tap()
    await control(page, 'Cancelar').tap()
    await control(page, 'Grabar explicación').tap()
    await control(page, 'Detener grabación').tap()
    await control(page, 'Escuchar mi grabación').tap()
    await control(page, 'Detener reproducción').tap()
    for (const button of await panel(page).getByRole('button').all()) {
      const box = (await button.boundingBox())!
      expect(box.height).toBeGreaterThanOrEqual(48)
      expect(box.width).toBeGreaterThanOrEqual(48)
      expect(box.x).toBeGreaterThanOrEqual(0)
      expect(box.x + box.width).toBeLessThanOrEqual(360)
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360)
    await control(page, 'Borrar').tap()
    await expect(control(page, 'Grabar explicación')).toBeVisible()
  })
})

test('real Chromium captures and plays synthetic microphone audio without uploads, storage or trace changes', async ({ playwright }) => {
  const browser = await playwright.chromium.launch({ args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] })
  const context = await browser.newContext({ baseURL: 'http://127.0.0.1:5173', permissions: ['microphone'] })
  const page = await context.newPage()
  const requests: { url: string; method: string }[] = []
  page.on('request', request => requests.push({ url: request.url(), method: request.method() }))
  try {
    await page.addInitScript(() => {
      const getStream = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices)
      window.syntheticStreams = []
      navigator.mediaDevices.getUserMedia = async constraints => {
        const stream = await getStream(constraints)
        window.syntheticStreams.push(stream)
        return stream
      }
    })
    await page.goto(harness)
    expect(await page.evaluate(() => window.syntheticStreams.length)).toBe(0)
    await finishAndAuthorize(page)
    expect(await page.evaluate(() => window.syntheticStreams.length)).toBe(0)
    const completed = await page.evaluate(() => window.attemptEvidence.current)
    await record(page)
    // Wait for measured capture time, without using a real microphone or saved fixture.
    await expect(panel(page).getByLabel('Tiempo de grabación')).toHaveText('1 / 30 s')
    await control(page, 'Detener grabación').click()
    // The default build (synthetic ASR preview off) offers no transcription and states that nothing is sent.
    await expect(control(page, 'Escuchar mi grabación')).toBeVisible()
    await expect(control(page, 'Transcribir prueba')).toHaveCount(0)
    await expect(panel(page).locator('.privacy-note')).toContainText('No se envía ni se transcribe.')
    await control(page, 'Escuchar mi grabación').click()
    const player = panel(page).locator('.recording-playback audio')
    await expect.poll(() => player.evaluate((audio: HTMLAudioElement) => audio.currentTime > 0)).toBe(true)
    await control(page, 'Detener reproducción').click()
    expect(await page.evaluate(() => window.syntheticStreams.every(stream => stream.getTracks().every(track => track.readyState === 'ended')))).toBe(true)
    expect(await page.evaluate(() => window.attemptEvidence.current)).toEqual(completed)
    expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
    expect(requests.every(request => request.method === 'GET' && ['127.0.0.1', ''].includes(new URL(request.url).hostname))).toBe(true)
    expect(requests.filter(request => /\/api\/|\/actuator\//.test(request.url))).toEqual([])
    await control(page, 'Borrar').click()
    await expect(player).toHaveCount(0)
  } finally { await browser.close() }
})

declare global { interface Window { syntheticStreams: MediaStream[] } }

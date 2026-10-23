// SPDX-License-Identifier: AGPL-3.0-or-later
import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { clips } from '../../src/features/guidance/clips.ts'
import type {} from './harness.tsx'

const harness = '/tests/e2e/harness.html?activity=app'
const listen = (page: Page) => page.getByRole('button', { name: 'Escuchar o repetir guía', exact: true })
const stop = (page: Page) => page.getByRole('button', { name: 'Detener guía', exact: true })
const audio = (page: Page) => page.locator('audio')

declare global {
  interface Window {
    guidanceMedia: {
      plays: { source: string; time: number }[]
      active: Set<HTMLMediaElement>
      mode: 'success' | 'reject' | 'pending'
      resolve: () => void
      reject: () => void
    }
  }
}

async function mockMedia(page: Page) {
  await page.addInitScript(() => {
    const media: Window['guidanceMedia'] = { plays: [], active: new Set(), mode: 'success', resolve: () => {}, reject: () => {} }
    window.guidanceMedia = media
    HTMLMediaElement.prototype.play = function () {
      media.plays.push({ source: this.getAttribute('src')!, time: this.currentTime })
      if (media.mode === 'reject') return Promise.reject(new DOMException('Blocked', 'NotAllowedError'))
      media.active.add(this)
      if (media.mode === 'pending') return new Promise<void>((resolve, reject) => {
        media.resolve = resolve
        media.reject = () => reject(new DOMException('Interrupted', 'AbortError'))
      })
      return Promise.resolve()
    }
    HTMLMediaElement.prototype.pause = function () { media.active.delete(this) }
  })
}

for (const activity of [
  { name: 'Bloques', clip: 'blocks', move: 'Agregar un bloque', reset: 'Intentar de nuevo' },
  { name: 'Secuencias', clip: 'descending', move: 'Saltar 1 a la izquierda', reset: 'Empezar de nuevo' },
  { name: 'Restas', clip: 'subtraction', move: 'Quitar 1 unidad', reset: 'Empezar de nuevo' },
] as const) {
  test(`${activity.name}: replay, stop and next-action guidance preserve the semantic attempt`, async ({ page }) => {
    await mockMedia(page)
    await page.goto(harness)
    await page.getByRole('button', { name: activity.name, exact: true }).click()
    await expect(audio(page)).toHaveAttribute('src', `/audio/${activity.clip}.wav`)
    expect(await page.evaluate(() => window.guidanceMedia.plays)).toEqual([])
    await listen(page).click()
    await expect(stop(page)).toBeEnabled()
    await listen(page).click()
    expect(await page.evaluate(() => window.guidanceMedia.plays.map(play => play.time))).toEqual([0, 0])
    expect(await page.evaluate(() => window.guidanceMedia.active.size)).toBe(1)
    await stop(page).click()
    await expect(stop(page)).toBeDisabled()
    expect(await page.evaluate(() => window.guidanceMedia.active.size)).toBe(0)
    await page.getByText('Ver instrucciones', { exact: true }).click()
    await expect(page.getByText(clips[activity.clip], { exact: true })).toBeVisible()
    await page.getByRole('button', { name: activity.move, exact: true }).click()
    await listen(page).click()
    await page.getByRole('button', { name: 'Listo', exact: true }).click()
    await expect(audio(page)).toHaveAttribute('src', '/audio/next.wav')
    expect(await page.evaluate(() => window.guidanceMedia.active.size)).toBe(0)
    // Completion focus still belongs to the existing reset control.
    await expect(page.getByRole('button', { name: activity.reset, exact: true })).toBeFocused()
    await expect.poll(() => page.evaluate(() => window.attemptEvidence.current)).not.toBeNull()
    const completed = await page.evaluate(() => window.attemptEvidence.current)
    expect(completed!.trace.events.map(event => event.kind)).toEqual([activity.name === 'Secuencias' ? 'jumpLeft' : activity.name === 'Restas' ? 'removeBlock' : 'placeBlock', 'finishAttempt'])
    await listen(page).click()
    await listen(page).click()
    expect(await page.evaluate(() => window.attemptEvidence.current)).toEqual(completed)
    await page.getByRole('button', { name: activity.reset, exact: true }).click()
    await expect(audio(page)).toHaveAttribute('src', `/audio/${activity.clip}.wav`)
    expect(await page.evaluate(() => window.guidanceMedia.active.size)).toBe(0)
  })
}

test('changing examples/activities and restarting unfinished exploration stops old guidance', async ({ page }) => {
  await mockMedia(page)
  await page.goto(harness)
  await listen(page).click()
  await page.getByRole('button', { name: 'Bloques', exact: true }).click()
  expect(await page.evaluate(() => window.guidanceMedia.active.size)).toBe(1)
  await page.getByRole('button', { name: 'Secuencias', exact: true }).click()
  expect(await page.evaluate(() => window.guidanceMedia.active.size)).toBe(0)
  await listen(page).click()
  await page.getByLabel('Elegí una secuencia').selectOption('ascending')
  await expect(audio(page)).toHaveAttribute('src', '/audio/ascending.wav')
  expect(await page.evaluate(() => window.guidanceMedia.active.size)).toBe(0)
  await listen(page).click()
  await page.getByRole('button', { name: 'Saltar 1 a la derecha', exact: true }).click()
  await page.getByRole('button', { name: 'Empezar de nuevo', exact: true }).click()
  expect(await page.evaluate(() => window.guidanceMedia.active.size)).toBe(0)
  await expect(stop(page)).toBeDisabled()
})

test('rejected playback, media errors and retries never prevent a visual attempt', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  await mockMedia(page)
  await page.goto(harness)
  await page.evaluate(() => { window.guidanceMedia.mode = 'reject' })
  await listen(page).click()
  await expect(page.getByText('No se pudo escuchar.', { exact: false })).toBeVisible()
  await page.evaluate(() => { window.guidanceMedia.mode = 'success' })
  await listen(page).click()
  await expect(stop(page)).toBeEnabled()
  await audio(page).dispatchEvent('error')
  await expect(page.getByText('No se pudo escuchar.', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: 'Agregar un bloque', exact: true }).click()
  await page.getByRole('button', { name: 'Listo', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('Terminaste')
  expect(errors).toEqual([])
})

test('late play promises cannot overwrite stop or a new screen', async ({ page }) => {
  await mockMedia(page)
  await page.goto(harness)
  await page.evaluate(() => { window.guidanceMedia.mode = 'pending' })
  await listen(page).click()
  await stop(page).click()
  await page.evaluate(() => window.guidanceMedia.resolve())
  await expect(stop(page)).toBeDisabled()
  await listen(page).click()
  await page.getByRole('button', { name: 'Restas', exact: true }).click()
  await page.evaluate(() => window.guidanceMedia.reject())
  await expect(stop(page)).toBeDisabled()
  await expect(page.getByText('No se pudo escuchar.', { exact: false })).toHaveCount(0)
})

for (const key of ['Enter', 'Space']) test(`playback can finish naturally; holding ${key} does not repeatedly start it`, async ({ page }) => {
  await mockMedia(page)
  await page.goto(harness)
  await listen(page).focus()
  await page.keyboard.down(key)
  await page.keyboard.down(key)
  await page.keyboard.down(key)
  await page.keyboard.up(key)
  expect(await page.evaluate(() => window.guidanceMedia.plays.length)).toBe(1)
  await audio(page).evaluate(element => Object.defineProperty(element, 'ended', { configurable: true, value: true }))
  await audio(page).dispatchEvent('ended')
  await expect(stop(page)).toBeDisabled()
})

for (const mode of ['success', 'pending'] as const) test(`stale ended events cannot disable stop during ${mode} replay`, async ({ page }) => {
  await mockMedia(page)
  await page.goto(harness)
  await listen(page).click()
  await expect(stop(page)).toBeEnabled()
  await page.evaluate(nextMode => { window.guidanceMedia.mode = nextMode }, mode)
  await listen(page).click()
  // The queued event is from the old playback; the rewound element is no longer ended.
  expect(await audio(page).evaluate((element: HTMLAudioElement) => element.ended)).toBe(false)
  await audio(page).dispatchEvent('ended')
  await expect(stop(page)).toBeEnabled()
  if (mode === 'pending') await page.evaluate(() => window.guidanceMedia.resolve())
  await expect(page.getByText('Escuchando la guía…', { exact: true })).toBeVisible()
  await stop(page).click()
  await expect(stop(page)).toBeDisabled()
  expect(await page.evaluate(() => window.guidanceMedia.active.size)).toBe(0)
})

test('hiding the page stops playback and does not resume it automatically', async ({ page }) => {
  await mockMedia(page)
  await page.goto(harness)
  await listen(page).click()
  await page.evaluate(() => {
    Object.defineProperty(document, 'hidden', { configurable: true, value: true })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  await expect(stop(page)).toBeDisabled()
  expect(await page.evaluate(() => window.guidanceMedia.active.size)).toBe(0)
  await page.evaluate(() => {
    Object.defineProperty(document, 'hidden', { configurable: true, value: false })
    document.dispatchEvent(new Event('visibilitychange'))
  })
  expect(await page.evaluate(() => window.guidanceMedia.plays.length)).toBe(1)
})

test('real browser plays, rewinds and stops the shipped audio; every clip decodes locally', async ({ page }) => {
  const requests: string[] = []
  await page.addInitScript(() => {
    navigator.mediaDevices.getUserMedia = () => { throw new Error('Unexpected microphone access') }
    window.speechSynthesis.speak = () => { throw new Error('Unexpected runtime synthesis') }
  })
  page.on('request', request => requests.push(request.url()))
  await page.goto(harness)
  expect(requests.filter(url => /\/audio\//.test(url))).toEqual([])
  await listen(page).click()
  await expect.poll(() => audio(page).evaluate((element: HTMLAudioElement) => !element.paused && element.currentTime > 0)).toBe(true)
  await audio(page).evaluate((element: HTMLAudioElement) => { element.currentTime = 4 })
  await listen(page).click()
  await expect.poll(() => audio(page).evaluate((element: HTMLAudioElement) => element.currentTime < 2)).toBe(true)
  await stop(page).click()
  expect(await audio(page).evaluate((element: HTMLAudioElement) => element.paused && element.currentTime === 0)).toBe(true)
  await listen(page).click()
  await expect(stop(page)).toBeEnabled()
  await audio(page).evaluate((element: HTMLAudioElement) => { element.currentTime = element.duration - 0.05 })
  await expect(stop(page)).toBeDisabled()
  expect(await audio(page).evaluate((element: HTMLAudioElement) => element.ended)).toBe(true)
  const durations = await page.evaluate(async ids => {
    const context = new AudioContext()
    try {
      return await Promise.all(ids.map(async id => {
        const response = await fetch(`/audio/${id}.wav`)
        if (!response.ok) throw new Error('Missing guidance asset')
        return (await context.decodeAudioData(await response.arrayBuffer())).duration
      }))
    } finally { await context.close() }
  }, Object.keys(clips))
  expect(durations.every(duration => duration > 0 && duration <= 15)).toBe(true)
  expect(requests.every(url => new URL(url).hostname === '127.0.0.1')).toBe(true)
  expect(requests.filter(url => /\/api\/|\/actuator\//.test(url))).toEqual([])
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
})

test('a missing real asset can be retried when the local file becomes available', async ({ page }) => {
  const url = '**/audio/blocks.wav'
  await page.route(url, route => route.fulfill({ status: 404, body: 'Missing synthetic fixture' }))
  await page.goto(harness)
  await listen(page).click()
  await expect(page.getByText('No se pudo escuchar.', { exact: false })).toBeVisible()
  await page.unroute(url)
  await listen(page).click()
  await expect.poll(() => audio(page).evaluate((element: HTMLAudioElement) => !element.paused && element.currentTime > 0)).toBe(true)
  await stop(page).click()
})

test.describe('touch guidance', () => {
  test.use({ hasTouch: true, viewport: { width: 360, height: 1000 } })
  test('listen/stop targets fit a phone and work by touch', async ({ page }) => {
    await mockMedia(page)
    await page.goto(harness)
    await listen(page).tap()
    await expect(stop(page)).toBeEnabled()
    await stop(page).tap()
    for (const control of [listen(page), stop(page)]) {
      const bounds = (await control.boundingBox())!
      expect(bounds.height).toBeGreaterThanOrEqual(48)
      expect(bounds.x).toBeGreaterThanOrEqual(0)
      expect(bounds.x + bounds.width).toBeLessThanOrEqual(360)
    }
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(360)
  })
})

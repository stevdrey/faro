/// <reference types="node" />
// SPDX-License-Identifier: AGPL-3.0-or-later
import { expect, test } from '@playwright/test'
import type { Browser, Page, Route } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import type {} from './harness.tsx'

// The synthetic preview server (see playwright.config.ts); the default suite stays on 5173 with it disabled.
const preview = 'http://127.0.0.1:5174'
const harness = '/tests/e2e/harness.html?activity=app'
const panel = (page: Page) => page.getByRole('region', { name: 'Contame cómo lo hiciste' })
const control = (page: Page, name: string) => panel(page).getByRole('button', { name, exact: true })
const hypothesis = { schemaVersion: 1, status: 'TRANSCRIBED', transcript: 'seis', engineId: 'synthetic',
  modelId: 'test', modelVersion: '1', latencyMs: 1, languageTag: 'es' }
async function prepare(page: Page, seconds = 1, sequence = false) {
  await page.goto(harness)
  if (sequence) await page.getByRole('button', { name: 'Secuencias', exact: true }).click()
  await page.getByRole('button', { name: 'Listo', exact: true }).click()
  await panel(page).getByRole('checkbox').check()
  await control(page, 'Habilitar grabación').click()
  await page.bringToFront()
  await control(page, 'Grabar explicación').click()
  await expect(control(page, 'Detener grabación')).toBeVisible()
  await expect(panel(page).getByLabel('Tiempo de grabación')).toHaveText(`${seconds} / 30 s`, { timeout: 10_000 })
  await control(page, 'Detener grabación').click()
  await expect(control(page, 'Escuchar mi grabación')).toBeVisible()
}
async function confirm(page: Page) { await panel(page).getByRole('checkbox', { name: 'Confirmo que esta grabación contiene solo audio sintético.' }).check() }

test.describe('synthetic ASR preview', () => {
  let browser: Browser
  test.beforeAll(async ({ playwright }) => {
    browser = await playwright.chromium.launch({ args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] })
  })
  test.afterAll(async () => browser.close())
  async function syntheticPage(useBrowserMicrophone = false) {
    const context = await browser.newContext({ baseURL: preview, permissions: ['microphone'] })
    const page = await context.newPage()
    if (!useBrowserMicrophone) await page.addInitScript(() => {
      // Lifecycle tests use a generated stream and real MediaRecorder without shared microphone-device state.
      navigator.mediaDevices.getUserMedia = async () => {
        const audio = new AudioContext()
        const destination = audio.createMediaStreamDestination()
        const source = audio.createOscillator()
        source.connect(destination)
        source.start()
        await audio.resume()
        for (const track of destination.stream.getTracks()) {
          const stop = track.stop.bind(track)
          track.stop = () => {
            if (track.readyState === 'ended') return
            stop()
            source.stop()
            void audio.close().catch(() => {})
          }
        }
        return destination.stream
      }
    })
    return page
  }
  test('normalization resamples 44.1 kHz stereo WAV to exact 16 kHz mono PCM in memory', async () => {
    const page = await syntheticPage()
    try {
      await page.goto(harness)
      const result = await page.evaluate(async () => {
        const path = '/src/features/recording/normalize.ts'
        const { normalizeClip } = await import(path)
        const frames = 4410 // 100 ms at 44.1 kHz; left=1, right=0, mean=0.5.
        const bytes = new ArrayBuffer(44 + frames * 4)
        const view = new DataView(bytes)
        const text = (offset: number, value: string) => [...value].forEach((character, index) => view.setUint8(offset + index, character.charCodeAt(0)))
        text(0, 'RIFF'); view.setUint32(4, bytes.byteLength - 8, true); text(8, 'WAVEfmt ')
        view.setUint32(16, 16, true); view.setUint16(20, 1, true); view.setUint16(22, 2, true)
        view.setUint32(24, 44100, true); view.setUint32(28, 44100 * 4, true)
        view.setUint16(32, 4, true); view.setUint16(34, 16, true); text(36, 'data')
        view.setUint32(40, frames * 4, true)
        for (let frame = 0; frame < frames; frame++) view.setInt16(44 + frame * 4, 32767, true)
        const pcm = await normalizeClip(new Blob([bytes], { type: 'audio/wav' }))
        return { bytes: pcm.byteLength, middleSample: new DataView(pcm).getInt16(800 * 2, true) }
      })
      expect(result.bytes).toBe(3200)
      // Native resampling filters can change a few PCM least-significant bits.
      // Exact PCM rounding/endianness are covered separately by the pure unit tests.
      expect(result.middleSample / 32768).toBeCloseTo(.5, 3)
    } finally { await page.context().close() }
  })
  test('real MediaRecorder audio is decoded and sent as PCM only on explicit activation', async () => {
    const page = await syntheticPage(true)
    const uploads: Buffer[] = []
    await page.route('**/api/v1/speech/transcriptions', async route => {
      expect(route.request().headers()['content-type']).toBe('application/octet-stream')
      uploads.push(route.request().postDataBuffer()!)
      await route.fulfill({ json: hypothesis, headers: { 'Cache-Control': 'no-store' } })
    })
    try {
      await prepare(page)
      const attempt = await page.evaluate(() => window.attemptEvidence.current)
      expect(uploads).toHaveLength(0)
      await expect(control(page, 'Transcribir prueba')).toBeDisabled()
      await confirm(page)
      await control(page, 'Transcribir prueba').focus()
      await page.keyboard.press('Enter')
      await expect(panel(page).getByText('Transcripción lista, sin confirmar.', { exact: true })).toBeVisible()
      expect(uploads).toHaveLength(1)
      expect(uploads[0].length).toBeGreaterThanOrEqual(16_000 * 2 * .9)
      expect(uploads[0].length).toBeLessThan(16_000 * 2 * 1.5)
      expect(uploads[0].length % 2).toBe(0)
      await panel(page).getByText('Ver hipótesis sin confirmar (prueba técnica)', { exact: true }).click()
      await expect(panel(page).getByText('seis', { exact: true })).toBeVisible()
      expect(await page.evaluate(() => window.attemptEvidence.current)).toEqual(attempt)
      expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
      await control(page, 'Borrar').click()
      await expect(panel(page).getByText('seis', { exact: true })).toHaveCount(0)
    } finally { await page.context().close() }
  })
  test('unsupported browser decoder makes no upload and leaves the attempt and clip available', async () => {
    const page = await syntheticPage()
    let uploads = 0
    await page.route('**/api/v1/speech/transcriptions', async route => { uploads++; await route.fulfill({ json: hypothesis }) })
    try {
      await prepare(page)
      const attempt = await page.evaluate(() => window.attemptEvidence.current)
      await page.evaluate(() => {
        OfflineAudioContext.prototype.decodeAudioData = () => Promise.reject(new Error('private synthetic vendor detail'))
      })
      await confirm(page)
      await control(page, 'Transcribir prueba').click()
      await expect(panel(page).getByText('Este formato de audio no se puede transcribir aquí.', { exact: false })).toBeVisible()
      expect(uploads).toBe(0)
      await expect(control(page, 'Escuchar mi grabación')).toBeEnabled()
      expect(await page.evaluate(() => window.attemptEvidence.current)).toEqual(attempt)
      await expect(page.getByText('private synthetic vendor detail')).toHaveCount(0)
      await page.getByRole('button', { name: 'Intentar de nuevo', exact: true }).click()
      await expect(page.getByRole('button', { name: 'Listo', exact: true })).toBeVisible()
    } finally { await page.context().close() }
  })
  test('unavailable ASR supports retry and empty/malformed results stay neutral', async () => {
    const page = await syntheticPage()
    let mode = 'unavailable'
    await page.route('**/api/v1/speech/transcriptions', async route => {
      if (mode === 'unavailable') await route.fulfill({ status: 503, json: { code: 'UNAVAILABLE' } })
      else await route.fulfill({ json: mode === 'empty' ? { ...hypothesis, status: 'EMPTY', transcript: '' } : {} })
    })
    try {
      await prepare(page)
      await confirm(page)
      await control(page, 'Transcribir prueba').click()
      await expect(panel(page).getByText('La transcripción local no está disponible.', { exact: false })).toBeVisible()
      mode = 'empty'
      await control(page, 'Transcribir prueba').click()
      await expect(panel(page).getByText('No se reconocieron palabras.', { exact: false })).toBeVisible()
      mode = 'malformed'
      await control(page, 'Transcribir prueba').click()
      await expect(panel(page).getByText('No se pudo obtener una transcripción.', { exact: false })).toBeVisible()
      await expect(page.getByText('Terminaste', { exact: true })).toBeVisible()
    } finally { await page.context().close() }
  })
  for (const action of ['cancel', 'delete', 'replace', 'reset', 'activity', 'example', 'hidden', 'pagehide', 'unmount'] as const) {
    test(`${action} invalidates pending transcription and late responses`, async () => {
      const page = await syntheticPage()
      let deliver!: () => Promise<void>
      const started = new Promise<void>(resolve => {
        void page.route('**/api/v1/speech/transcriptions', (route: Route) => {
          deliver = async () => { try { await route.fulfill({ json: hypothesis }) } catch { /* HTTP may already be aborted. */ } }
          resolve()
        })
      })
      try {
        await prepare(page, 1, action === 'example')
        await confirm(page)
        await control(page, 'Transcribir prueba').click()
        await started
        if (action === 'cancel') await control(page, 'Cancelar transcripción').click()
        if (action === 'delete') await control(page, 'Borrar').click()
        if (action === 'replace') await control(page, 'Grabar de nuevo').click()
        if (action === 'reset') await page.getByRole('button', { name: 'Intentar de nuevo', exact: true }).click()
        if (action === 'activity') await page.getByRole('button', { name: 'Restas', exact: true }).click()
        if (action === 'example') await page.getByRole('combobox').selectOption('ascending')
        if (action === 'hidden') await page.evaluate(() => {
          Object.defineProperty(document, 'hidden', { configurable: true, value: true })
          document.dispatchEvent(new Event('visibilitychange'))
        })
        if (action === 'pagehide') await page.evaluate(() => window.dispatchEvent(new Event('pagehide')))
        if (action === 'unmount') await page.evaluate(() => window.unmountHarness())
        await deliver()
        await expect(page.getByText('Transcribiendo localmente…', { exact: true })).toHaveCount(0)
        await expect(page.getByText('Transcripción lista, sin confirmar.', { exact: true })).toHaveCount(0)
        if (action === 'cancel') await expect(control(page, 'Escuchar mi grabación')).toBeVisible()
      } finally { await page.context().close() }
    })
  }
})

test('optional real browser → Faro API → pinned local whisper.cpp smoke', async ({ playwright }) => {
  test.skip(process.env.FARO_REAL_ASR_SMOKE !== 'true', 'Requires explicitly started local synthetic-test API and pinned baseline; no downloads in CI.')
  const directory = mkdtempSync(join(tmpdir(), 'faro-browser-asr-'))
  let browser: Browser | undefined
  try {
    const manifest = JSON.parse(readFileSync(new URL('../../../../infra/whisper/artifacts.json', import.meta.url), 'utf8'))
    const original = join(directory, 'synthetic.wav')
    const wav = join(directory, 'microphone.wav')
    execFileSync('espeak-ng', ['-v', manifest.sample.voice, '-s', String(manifest.sample.words_per_minute), '-p', String(manifest.sample.pitch), '-w', original, manifest.sample.text], { stdio: 'ignore' })
    execFileSync('ffmpeg', ['-nostdin', '-hide_banner', '-loglevel', 'error', '-i', original, '-ar', '48000', '-ac', '2', '-c:a', 'pcm_s16le', wav], { stdio: 'ignore' })
    browser = await playwright.chromium.launch({ args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream', `--use-file-for-fake-audio-capture=${wav}`] })
    const context = await browser.newContext({ baseURL: preview, permissions: ['microphone'] })
    const page = await context.newPage()
    await prepare(page, 5)
    await confirm(page)
    const response = page.waitForResponse('**/api/v1/speech/transcriptions')
    await control(page, 'Transcribir prueba').click()
    const result = await (await response).json()
    // Assertions contain no transcript contents, even on failure. No saved browser artifacts.
    expect(result.status === 'TRANSCRIBED').toBe(true)
    expect(typeof result.transcript === 'string' && result.transcript.trim().length > 0).toBe(true)
    expect(result.engineId).toBe('whisper-cpp')
    expect(result.modelId).toBe(manifest.model.id)
    expect(result.modelVersion).toBe(manifest.model.revision)
    expect(result.languageTag).toBe('es')
    await expect(panel(page).getByText('Transcripción lista, sin confirmar.', { exact: true })).toBeVisible()
    await control(page, 'Borrar').click()
    console.log(JSON.stringify({ status: 'passed', engine: result.engineId, language: result.languageTag, latencyMs: result.latencyMs, transcriptCharacters: result.transcript.length }))
  } finally { await browser?.close(); rmSync(directory, { recursive: true, force: true }) }
})

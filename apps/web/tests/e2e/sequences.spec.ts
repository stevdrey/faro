// SPDX-License-Identifier: AGPL-3.0-or-later
import { expect, test } from '@playwright/test'
import type {} from './harness.tsx'

const harness = '/tests/e2e/harness.html?activity=sequences'

test('the real app offers both sequences, forgets switched attempts, and needs no API or storage', async ({ page }) => {
  const requests: string[] = []
  page.on('request', request => { if (/\/api\/|\/actuator\//.test(request.url())) requests.push(request.url()) })
  await page.goto('/')
  await expect(page.getByTestId('equation')).toHaveText('12+□=18')
  await page.getByRole('button', { name: 'Secuencias', exact: true }).click()
  await expect(page.getByTestId('sequence-terms')).toHaveText('30,28,□')
  await expect(page.getByRole('status')).toHaveText('Estás en 28')
  await expect(page.getByRole('radio', { name: '1', exact: true })).toBeChecked()
  await page.getByRole('radio', { name: '2', exact: true }).check()
  await page.getByRole('button', { name: 'Saltar 2 a la izquierda' }).click()
  await expect(page.getByRole('status')).toHaveText('Estás en 26')
  await expect(page.getByRole('listitem')).toHaveText(['De 28 a 26: 2 a la izquierda.'])
  await page.getByRole('combobox', { name: 'Elegí una secuencia' }).selectOption('ascending')
  await expect(page.getByTestId('sequence-terms')).toHaveText('10,12,□')
  await expect(page.getByRole('status')).toHaveText('Estás en 12')
  await expect(page.getByTestId('jump-arc')).toHaveCount(0)
  await expect(page.getByRole('radio', { name: '1', exact: true })).toBeChecked()
  await page.getByRole('radio', { name: '2', exact: true }).check()
  await page.getByRole('button', { name: 'Saltar 2 a la derecha' }).click()
  await expect(page.getByRole('status')).toHaveText('Estás en 14')
  await page.getByRole('button', { name: 'Bloques', exact: true }).click()
  await page.getByRole('button', { name: 'Secuencias', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('Estás en 12')
  await expect(page.getByTestId('jump-arc')).toHaveCount(0)
  expect(await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length }))).toEqual({ local: 0, session: 0 })
  expect(requests).toEqual([])
  await page.reload()
  await expect(page.getByTestId('equation')).toBeVisible()
  await page.getByRole('button', { name: 'Secuencias', exact: true }).click()
  await expect(page.getByRole('status')).toHaveText('Estás en 28')
})

test('every size and direction produces a visible jump and exact immutable semantic evidence', async ({ page }) => {
  await page.goto(harness)
  for (const size of [1, 2, 5, 10]) {
    await page.getByRole('radio', { name: `${size}`, exact: true }).check()
    await page.getByRole('button', { name: `Saltar ${size} a la izquierda` }).click()
    await page.getByRole('button', { name: `Saltar ${size} a la derecha` }).click()
  }
  await expect(page.getByTestId('jump-arc')).toHaveCount(8)
  await expect(page.getByRole('listitem')).toHaveText([
    'De 28 a 27: 1 a la izquierda.', 'De 27 a 28: 1 a la derecha.',
    'De 28 a 26: 2 a la izquierda.', 'De 26 a 28: 2 a la derecha.',
    'De 28 a 23: 5 a la izquierda.', 'De 23 a 28: 5 a la derecha.',
    'De 28 a 18: 10 a la izquierda.', 'De 18 a 28: 10 a la derecha.',
  ])
  await page.getByRole('button', { name: 'Listo' }).dblclick()
  await expect(page.getByRole('status')).toHaveText('Terminaste')
  await expect(page.getByRole('button', { name: 'Empezar de nuevo' })).toBeFocused()
  const evidence = await page.evaluate(() => window.screenEvidence)
  expect(evidence.completions).toHaveLength(1)
  expect(evidence.frozen).toBe(true)
  const trace = evidence.completions[0]
  expect(trace.initialValue).toBe(28)
  expect(trace.schemaVersion).toBe(1)
  expect(trace.events.map(({ occurredAt, ...event }) => event)).toEqual([
    ...[1, 2, 5, 10].flatMap((value, index) => [
      { kind: 'jumpLeft', value, step: index * 2 + 1 },
      { kind: 'jumpRight', value, step: index * 2 + 2 },
    ]), { kind: 'finishAttempt', step: 9 },
  ])
  for (const event of trace.events) expect(event.occurredAt).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)
  await expect(page.getByRole('button', { name: 'Saltar 10 a la izquierda' })).toBeDisabled()
  await expect(page.getByRole('radio', { name: '2', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Saltar 10 a la derecha' }).dispatchEvent('click')
  await page.getByRole('button', { name: 'Listo' }).dispatchEvent('click')
  expect(await page.evaluate(() => window.screenEvidence.completions)).toEqual(evidence.completions)
  await expect(page.getByTestId('jump-arc')).toHaveCount(8)
  await page.getByRole('button', { name: 'Empezar de nuevo' }).dblclick()
  await expect(page.getByRole('status')).toHaveText('Estás en 28')
  await expect(page.getByTestId('jump-arc')).toHaveCount(0)
  await expect(page.getByRole('radio', { name: '1', exact: true })).toBeChecked()
  expect(await page.evaluate(() => window.screenEvidence.resets)).toBe(1)
  await page.getByRole('button', { name: 'Listo' }).click()
  const restarted = await page.evaluate(() => window.screenEvidence.completions[1])
  expect(restarted.events).toHaveLength(1)
  expect(restarted.events[0].step).toBe(1)
})

for (const direction of ['none', 'izquierda', 'derecha']) {
  test(`completion stays neutral with ${direction} exploration`, async ({ page }) => {
    await page.goto(harness)
    if (direction !== 'none') {
      await page.getByRole('radio', { name: '2', exact: true }).check()
      await page.getByRole('button', { name: `Saltar 2 a la ${direction}` }).click()
    }
    await page.getByRole('button', { name: 'Listo' }).click()
    await expect(page.getByRole('status')).toHaveText('Terminaste')
    await expect(page.getByTestId('sequence-terms')).toHaveText('30,28,□')
    await expect(page.getByText(/correcto|incorrecto|muy bien|error/i)).toHaveCount(0)
    expect(await page.evaluate(() => window.screenEvidence.completions.length)).toBe(1)
  })
}

for (const key of ['Enter', 'Space']) {
  test(`keyboard ${key} supports corrections and holding finish cannot reset or finish a new attempt`, async ({ page }) => {
    await page.goto(harness)
    await page.getByRole('radio', { name: '1', exact: true }).focus()
    await page.keyboard.press('ArrowRight')
    await expect(page.getByRole('radio', { name: '2', exact: true })).toBeChecked()
    await page.getByRole('button', { name: 'Saltar 2 a la izquierda' }).focus()
    await page.keyboard.press(key)
    await expect(page.getByRole('status')).toHaveText('Estás en 26')
    await page.getByRole('button', { name: 'Saltar 2 a la derecha' }).focus()
    await page.keyboard.press(key)
    await expect(page.getByRole('status')).toHaveText('Estás en 28')
    await page.getByRole('button', { name: 'Listo' }).focus()
    await page.keyboard.down(key)
    for (let repeat = 0; repeat < 3; repeat++) await page.keyboard.down(key)
    await page.keyboard.up(key)
    await expect(page.getByRole('status')).toHaveText('Terminaste')
    await expect(page.getByRole('button', { name: 'Empezar de nuevo' })).toBeFocused()
    expect(await page.evaluate(() => window.screenEvidence.resets)).toBe(0)
    await page.keyboard.press(key)
    await expect(page.getByRole('status')).toHaveText('Estás en 28')
    await expect(page.getByTestId('jump-arc')).toHaveCount(0)
    expect(await page.evaluate(() => window.screenEvidence.completions.length)).toBe(1)
  })
}

for (const width of [320, 360, 700, 1100]) {
  test(`all repeated and negative jumps remain available without page overflow at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    await page.goto(harness)
    await page.getByRole('radio', { name: '10', exact: true }).check()
    for (let index = 0; index < 4; index++) await page.getByRole('button', { name: 'Saltar 10 a la izquierda' }).click()
    for (let index = 0; index < 4; index++) await page.getByRole('button', { name: 'Saltar 10 a la derecha' }).click()
    await expect(page.getByRole('listitem')).toHaveCount(8)
    await expect(page.getByText('De -2 a -12: 10 a la izquierda.', { exact: true })).toBeVisible()
    await expect(page.getByTestId('jump-arc')).toHaveCount(8)
    const geometry = await page.evaluate(() => {
      const svg = document.querySelector<SVGSVGElement>('.number-line')!
      return {
        viewport: innerWidth, document: document.documentElement.scrollWidth,
        controls: [...document.querySelectorAll('button, .jump-sizes label')].map(element => {
          const bounds = element.getBoundingClientRect()
          return { left: bounds.left, right: bounds.right, height: bounds.height }
        }),
        inside: [...svg.querySelectorAll<SVGGraphicsElement>('[data-testid="jump-arc"]')].every(element => {
          const bounds = element.getBBox()
          return bounds.x >= 0 && bounds.y >= 0 && bounds.x + bounds.width <= svg.viewBox.baseVal.width && bounds.y + bounds.height <= svg.viewBox.baseVal.height
        }),
      }
    })
    expect(geometry.document).toBeLessThanOrEqual(geometry.viewport)
    expect(geometry.inside).toBe(true)
    for (const control of geometry.controls) {
      expect(control.left).toBeGreaterThanOrEqual(0)
      expect(control.right).toBeLessThanOrEqual(width)
      expect(control.height).toBeGreaterThanOrEqual(48)
    }
    const line = page.getByRole('region', { name: /Recta numérica/ })
    await line.focus()
    await page.keyboard.press('Home')
    await page.keyboard.press('ArrowRight')
    await expect(page.getByTestId('jump-arc')).toHaveCount(8)
  })
}

test.describe('touch sequence controls', () => {
  test.use({ viewport: { width: 360, height: 900 }, hasTouch: true, isMobile: true })
  test('taps choose size, jump in both directions and reset an unfinished attempt', async ({ page }) => {
    await page.goto(`${harness}&example=ascending`)
    await page.getByRole('radio', { name: '5', exact: true }).tap()
    await page.getByRole('button', { name: 'Saltar 5 a la derecha' }).tap()
    await expect(page.getByRole('status')).toHaveText('Estás en 17')
    await page.getByRole('button', { name: 'Saltar 5 a la izquierda' }).tap()
    await expect(page.getByRole('status')).toHaveText('Estás en 12')
    await expect(page.getByRole('listitem')).toHaveCount(2)
    await page.getByRole('button', { name: 'Empezar de nuevo' }).tap()
    await expect(page.getByTestId('jump-arc')).toHaveCount(0)
    await page.getByRole('button', { name: 'Listo' }).tap()
    expect(await page.evaluate(() => window.screenEvidence.completions[0].initialValue)).toBe(12)
  })
})

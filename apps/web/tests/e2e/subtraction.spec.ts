// SPDX-License-Identifier: AGPL-3.0-or-later
import { expect, test } from '@playwright/test'
import type { Locator, Page } from '@playwright/test'
import sharedFixture from '../../../../contracts/actions/v2/fixtures/subtraction.json' with { type: 'json' }
import type { ActionTracePayloadV2 } from '../../src/contracts/action-v2.ts'
import type {} from './harness.tsx'

const harness = '/tests/e2e/harness.html?activity=subtraction'
const decompose = 'Cambiar 1 decena por 10 unidades'
const compose = 'Cambiar 10 unidades por 1 decena'
const fixture = sharedFixture as ActionTracePayloadV2
const button = (page: Page, name: string) => page.getByRole('button', { name, exact: true })
async function quantities(page: Page, remaining: number, removed: number) {
  await expect(page.getByTestId('available-total')).toHaveText(String(remaining))
  await expect(page.getByTestId('removed-total')).toHaveText(String(removed))
  await expect(page.getByTestId('subtraction-equation')).toHaveText('53−28=□')
}
async function center(locator: Locator) {
  const bounds = await locator.boundingBox()
  if (!bounds) throw new Error('Expected a visible piece or tray')
  return { x: bounds.x + bounds.width / 2, y: bounds.y + bounds.height / 2 }
}
async function drag(page: Page, source: Locator, target: Locator) {
  await source.scrollIntoViewIfNeeded()
  const start = await center(source)
  const end = await center(target)
  await page.mouse.move(start.x, start.y)
  await page.mouse.down()
  await page.mouse.move(end.x, end.y, { steps: 10 })
  await page.mouse.up()
}

test('real navigation discards attempts, requires no API and writes no storage', async ({ page }) => {
  const requests: string[] = []
  page.on('request', request => { if (/\/api\/|\/actuator\//.test(request.url())) requests.push(request.url()) })
  await page.goto('/')
  await button(page, 'Restas').click()
  await quantities(page, 53, 0)
  await button(page, 'Quitar 1 decena').click()
  await quantities(page, 43, 10)
  await button(page, 'Secuencias').click()
  await button(page, 'Restas').click()
  await quantities(page, 53, 0)
  await button(page, decompose).click()
  await button(page, 'Bloques').click()
  await expect(page.getByTestId('equation')).toHaveText('12+□=18')
  await button(page, 'Restas').click()
  await expect(page.getByTestId('available-counts')).toHaveText('5 decenas y 3 unidades')
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expect(requests).toEqual([])
  await page.reload()
  await button(page, 'Restas').click()
  await quantities(page, 53, 0)
})

test('twenty then eight with manual exchanges emits the exact shared v2 trace once', async ({ page }) => {
  await page.goto(harness)
  for (const event of fixture.events) {
    const label = event.kind === 'decomposeTen' ? decompose : event.kind === 'composeTen' ? compose
      : event.kind === 'finishAttempt' ? 'Listo'
      : `${event.kind === 'removeBlock' ? 'Quitar' : 'Devolver'} 1 ${'value' in event && event.value === 10 ? 'decena' : 'unidad'}`
    if (event.kind === 'finishAttempt') await button(page, label).dblclick()
    else await button(page, label).click()
    if (event.step === 2) await quantities(page, 33, 20)
    if (event.step === 3) await expect(page.getByTestId('available-counts')).toHaveText('2 decenas y 13 unidades')
  }
  await quantities(page, 25, 28)
  await expect(page.getByRole('status')).toHaveText('Terminaste')
  await expect(button(page, 'Empezar de nuevo')).toBeFocused()
  const evidence = await page.evaluate(() => window.screenEvidence)
  expect(evidence.completions).toHaveLength(1)
  expect(evidence.frozen).toBe(true)
  const trace = evidence.completions[0]
  expect(trace.schemaVersion).toBe(2)
  expect(trace.initialValue).toBe(53)
  expect(trace.events.map(({ occurredAt, ...event }) => event)).toEqual(fixture.events.map(({ occurredAt, ...event }) => event))
  for (const event of trace.events) expect(event.occurredAt).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/)
  for (const label of ['Quitar 1 decena', 'Devolver 1 unidad', decompose, compose, 'Listo']) {
    await expect(button(page, label)).toBeDisabled()
    await button(page, label).dispatchEvent('click')
  }
  expect(await page.evaluate(() => window.screenEvidence)).toEqual(evidence)
  await button(page, 'Empezar de nuevo').dblclick()
  await quantities(page, 53, 0)
  expect(await page.evaluate(() => window.screenEvidence.resets)).toBe(1)
  await button(page, 'Listo').click()
  expect(await page.evaluate(() => window.screenEvidence.completions[1].events.map(event => event.step))).toEqual([1])
})

for (const units of [0, 1, 3]) {
  test(`completion after removing ${units} units remains neutral`, async ({ page }) => {
    await page.goto(harness)
    for (let i = 0; i < units; i++) await button(page, 'Quitar 1 unidad').click()
    await button(page, 'Listo').click()
    await expect(page.getByRole('status')).toHaveText('Terminaste')
    await quantities(page, 53 - units, units)
    await expect(page.getByText(/correcto|incorrecto|muy bien|error/i)).toHaveCount(0)
  })
}

test('pieces run out without automatic borrowing, and all 53 can be removed and returned', async ({ page }) => {
  await page.goto(harness)
  for (let i = 0; i < 3; i++) await button(page, 'Quitar 1 unidad').click()
  await expect(button(page, 'Quitar 1 unidad')).toBeDisabled()
  await expect(button(page, compose)).toBeDisabled()
  await button(page, 'Quitar 1 unidad').dispatchEvent('click')
  await quantities(page, 50, 3)
  await expect(page.getByTestId('available-counts')).toHaveText('5 decenas y 0 unidades')
  for (let i = 0; i < 5; i++) await button(page, 'Quitar 1 decena').click()
  await quantities(page, 0, 53)
  await expect(button(page, decompose)).toBeDisabled()
  for (let i = 0; i < 5; i++) await button(page, 'Devolver 1 decena').click()
  for (let i = 0; i < 3; i++) await button(page, 'Devolver 1 unidad').click()
  await quantities(page, 53, 0)
  await expect(button(page, 'Devolver 1 unidad')).toBeDisabled()
  await expect(button(page, 'Devolver 1 decena')).toBeDisabled()
  await button(page, decompose).click()
  await button(page, 'Empezar de nuevo').click()
  await expect(page.getByTestId('available-counts')).toHaveText('5 decenas y 3 unidades')
})

test('mouse drags tens and units both ways; Escape, invalid drop and capture loss cancel', async ({ page }) => {
  await page.goto(harness)
  for (const [piece, value] of [['ten', 10], ['one', 1]] as const) {
    await drag(page, page.getByTestId(`available-${piece}`).first(), page.getByTestId('removed-area'))
    await quantities(page, 53 - value, value)
    await drag(page, page.getByTestId(`removed-${piece}`).first(), page.getByTestId('available-area'))
    await quantities(page, 53, 0)
  }
  const source = page.getByTestId('available-one').first()
  for (const cancel of ['Escape', 'outside', 'capture']) {
    const start = await center(source)
    const end = await center(page.getByTestId('removed-area'))
    await page.mouse.move(start.x, start.y)
    await page.mouse.down()
    await page.mouse.move(end.x, end.y, { steps: 5 })
    if (cancel === 'Escape') await page.keyboard.press('Escape')
    if (cancel === 'outside') await page.mouse.move(2, 2)
    if (cancel === 'capture') await source.dispatchEvent('lostpointercapture', { pointerId: 1 })
    await page.mouse.up()
    await quantities(page, 53, 0)
  }
  await button(page, 'Listo').click()
  expect(await page.evaluate(() => window.screenEvidence.completions[0].events)).toHaveLength(5)
})

for (const key of ['Enter', 'Space']) {
  test(`${key} supports movements and exchanges; holding a key records only one action`, async ({ page }) => {
    await page.goto(harness)
    for (const label of ['Quitar 1 decena', 'Devolver 1 decena', decompose, compose]) {
      await button(page, label).focus()
      for (let i = 0; i < 4; i++) await page.keyboard.down(key)
      await page.keyboard.up(key)
    }
    await quantities(page, 53, 0)
    await expect(page.getByTestId('available-counts')).toHaveText('5 decenas y 3 unidades')
    await button(page, 'Listo').focus()
    for (let i = 0; i < 4; i++) await page.keyboard.down(key)
    await page.keyboard.up(key)
    await expect(button(page, 'Empezar de nuevo')).toBeFocused()
    expect(await page.evaluate(() => window.screenEvidence.resets)).toBe(0)
    expect(await page.evaluate(() => window.screenEvidence.completions[0].events)).toHaveLength(5)
    await page.keyboard.press(key)
    await quantities(page, 53, 0)
    expect(await page.evaluate(() => window.screenEvidence.resets)).toBe(1)
  })
}

for (const width of [320, 360, 700, 1100]) {
  test(`all decomposed pieces and controls fit at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1100 })
    await page.goto(harness)
    for (let i = 0; i < 5; i++) await button(page, decompose).click()
    await expect(page.getByTestId('available-one')).toHaveCount(53)
    await expect(page.getByTestId('available-counts')).toHaveText('0 decenas y 53 unidades')
    const geometry = await page.evaluate(() => ({
      width: innerWidth, document: document.documentElement.scrollWidth,
      controls: [...document.querySelectorAll('button, .math-piece')].map(element => {
        const b = element.getBoundingClientRect()
        return { left: b.left, right: b.right, height: b.height }
      }),
    }))
    expect(geometry.document).toBeLessThanOrEqual(width)
    for (const control of geometry.controls) {
      expect(control.left).toBeGreaterThanOrEqual(0)
      expect(control.right).toBeLessThanOrEqual(width)
      expect(control.height).toBeGreaterThanOrEqual(48)
    }
  })
}

test('touch drags both kinds of piece, cancels gestures, and supports tap exchanges', async ({ browser }) => {
  const context = await browser.newContext({ viewport: { width: 360, height: 1600 }, hasTouch: true, isMobile: true })
  const page = await context.newPage()
  await page.goto(`http://127.0.0.1:5173${harness}`)
  const session = await context.newCDPSession(page)
  async function touchDrag(source: Locator, target: Locator, cancel = false) {
    await source.scrollIntoViewIfNeeded()
    const start = await center(source)
    const end = await center(target)
    await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [start] })
    for (let i = 1; i <= 5; i++) await session.send('Input.dispatchTouchEvent', {
      type: 'touchMove', touchPoints: [{ x: start.x + (end.x - start.x) * i / 5, y: start.y + (end.y - start.y) * i / 5 }],
    })
    await session.send('Input.dispatchTouchEvent', { type: cancel ? 'touchCancel' : 'touchEnd', touchPoints: [] })
  }
  for (const [piece, value] of [['ten', 10], ['one', 1]] as const) {
    await touchDrag(page.getByTestId(`available-${piece}`).first(), page.getByTestId('removed-area'))
    await quantities(page, 53 - value, value)
    await touchDrag(page.getByTestId(`removed-${piece}`).first(), page.getByTestId('available-area'))
    await quantities(page, 53, 0)
  }
  await touchDrag(page.getByTestId('available-one').first(), page.getByTestId('removed-area'), true)
  await quantities(page, 53, 0)
  await button(page, decompose).tap()
  await expect(page.getByTestId('available-counts')).toHaveText('4 decenas y 13 unidades')
  await button(page, compose).tap()
  await button(page, 'Quitar 1 unidad').tap()
  await button(page, 'Devolver 1 unidad').tap()
  await button(page, 'Listo').tap()
  expect(await page.evaluate(() => window.screenEvidence.completions[0].events)).toHaveLength(9)
  await context.close()
})

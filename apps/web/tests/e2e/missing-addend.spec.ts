// SPDX-License-Identifier: AGPL-3.0-or-later
import { expect, test } from '@playwright/test'
import type { Locator, Page } from '@playwright/test'
import type {} from './harness.tsx'

async function center(locator: Locator) {
  const box = await locator.boundingBox()
  if (!box) throw new Error('Expected a visible drag target')
  return { x: box.x + box.width / 2, y: box.y + box.height / 2 }
}

async function dragBlock(page: Page, source: Locator, destination: Locator) {
  const start = await center(source)
  const end = await center(destination)
  await page.mouse.move(start.x, start.y)
  await page.mouse.down()
  await page.mouse.move(end.x, end.y, { steps: 10 })
  await page.mouse.up()
}

async function expectCount(page: Page, count: number) {
  await expect(page.getByTestId('answer-block')).toHaveCount(count)
  await expect(page.getByTestId('reserve-block')).toHaveCount(10 - count)
  await expect(page.getByRole('heading', { name: `Vos agregás ${count}`, exact: true })).toBeVisible()
}

test('the real app works without an API, stores no attempt, and supports keyboard corrections', async ({ page }) => {
  const requests: string[] = []
  page.on('request', request => { if (/\/api\/|\/actuator\//.test(request.url())) requests.push(request.url()) })
  await page.goto('/')
  await expect(page.getByTestId('equation')).toHaveText('12+□=18')
  await expectCount(page, 0)
  await expect(page.getByRole('button', { name: 'Quitar un bloque' })).toBeDisabled()
  const add = page.getByRole('button', { name: 'Agregar un bloque' })
  await add.focus()
  await page.keyboard.press('Enter')
  await page.keyboard.press('Space')
  await expectCount(page, 2)
  const remove = page.getByRole('button', { name: 'Quitar un bloque' })
  await remove.focus()
  await page.keyboard.press('Space')
  await expectCount(page, 1)
  for (let i = 1; i < 10; i++) await add.click()
  await expectCount(page, 10)
  await expect(add).toBeDisabled()
  await page.getByRole('button', { name: 'Listo' }).click()
  await expect(page.getByRole('status')).toHaveText('Terminaste')
  await expect(remove).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Intentar de nuevo' })).toBeFocused()
  await page.getByRole('button', { name: 'Intentar de nuevo' }).click()
  await expectCount(page, 0)
  await expect(page.getByRole('button', { name: 'Listo' })).toBeFocused()
  expect(await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length }))).toEqual({ local: 0, session: 0 })
  expect(requests).toEqual([])
  await add.click()
  await page.reload()
  await expectCount(page, 0)
})

for (const key of ['Enter', 'Space']) {
  test(`holding ${key} cannot restart a completed attempt or finish the next one`, async ({ page }) => {
    await page.goto('/tests/e2e/harness.html')
    for (let i = 0; i < 6; i++) await page.getByRole('button', { name: 'Agregar un bloque' }).click()
    await page.getByRole('button', { name: 'Listo' }).focus()

    async function holdAndRelease() {
      try {
        await page.keyboard.down(key)
        for (let repeat = 0; repeat < 3; repeat++) {
          // Repeated down calls set KeyboardEvent.repeat without releasing the key.
          await page.keyboard.down(key)
          await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => resolve())))
        }
      } finally {
        await page.keyboard.up(key)
      }
    }

    await holdAndRelease()
    await expectCount(page, 6)
    await expect(page.getByRole('status')).toHaveText('Terminaste')
    await expect(page.getByRole('button', { name: 'Intentar de nuevo' })).toBeFocused()
    const completed = await page.evaluate(() => window.screenEvidence)
    expect(completed.completions).toHaveLength(1)
    expect(completed.resets).toBe(0)
    expect(completed.completions[0].events).toHaveLength(7)
    expect(completed.completions[0].events.at(-1)?.kind).toBe('finishAttempt')

    // A new key press deliberately resets, but holding it must not finish at zero.
    await holdAndRelease()
    await expectCount(page, 0)
    await expect(page.getByRole('status')).toHaveText('A tu ritmo.')
    await expect(page.getByRole('button', { name: 'Listo' })).toBeFocused()
    const reset = await page.evaluate(() => window.screenEvidence)
    expect(reset.completions).toHaveLength(1)
    expect(reset.resets).toBe(1)
    await page.keyboard.press(key)
    await expect(page.getByRole('status')).toHaveText('Terminaste')
    await expect.poll(() => page.evaluate(() => window.screenEvidence.completions.length)).toBe(2)
  })
}

for (const width of [320, 360, 640, 641, 695, 700, 701, 768, 1100]) {
  test(`panels and controls fit at ${width}px with zero and ten blocks`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 })
    await page.goto('/')

    async function expectHorizontalFit() {
      const geometry = await page.evaluate(() => ({
        viewport: innerWidth,
        document: document.documentElement.scrollWidth,
        elements: [...document.querySelectorAll('.known-panel, .answer-panel, .reserve-panel, button')].map(element => {
          const { left, right } = element.getBoundingClientRect()
          return { left, right }
        }),
      }))
      expect(geometry.document).toBeLessThanOrEqual(geometry.viewport)
      for (const element of geometry.elements) {
        expect(element.left).toBeGreaterThanOrEqual(0)
        expect(element.right).toBeLessThanOrEqual(geometry.viewport)
      }
    }

    await expectCount(page, 0)
    await expectHorizontalFit()
    for (let i = 0; i < 10; i++) await page.getByRole('button', { name: 'Agregar un bloque' }).click()
    await expectCount(page, 10)
    await expectHorizontalFit()
    await page.getByRole('button', { name: 'Listo' }).click()
    await expectHorizontalFit()
    await page.getByRole('button', { name: 'Intentar de nuevo' }).click()
    await expectCount(page, 0)
  })
}

test('mouse drag supports corrections, outside drops, Escape, and pointer cancellation without phantom events', async ({ page }) => {
  await page.goto('/tests/e2e/harness.html')
  const reserve = page.getByTestId('reserve-area')
  const answer = page.getByTestId('answer-area')
  await dragBlock(page, page.getByTestId('reserve-block').first(), answer)
  await expectCount(page, 1)
  await dragBlock(page, page.getByTestId('answer-block').first(), reserve)
  await expectCount(page, 0)
  const source = page.getByTestId('reserve-block').first()
  const start = await center(source)
  const end = await center(answer)
  await page.mouse.move(start.x, start.y)
  await page.mouse.down()
  await page.mouse.move(3, 3, { steps: 4 })
  await page.mouse.up()
  await expectCount(page, 0)
  for (const cancel of ['Escape', 'pointercancel', 'lostpointercapture']) {
    await page.mouse.move(start.x, start.y)
    await page.mouse.down()
    await page.mouse.move(end.x, end.y, { steps: 4 })
    if (cancel === 'Escape') await page.keyboard.press('Escape')
    else await source.dispatchEvent(cancel, { pointerId: 1, isPrimary: true })
    await page.mouse.up()
    await expectCount(page, 0)
  }
  await page.getByRole('button', { name: 'Listo' }).click()
  const evidence = await page.evaluate(() => window.screenEvidence)
  expect(evidence.completions).toHaveLength(1)
  expect(evidence.frozen).toBe(true)
  expect(evidence.completions[0].events.map(event => event.kind)).toEqual(['placeBlock', 'removeBlock', 'finishAttempt'])
  expect(evidence.completions[0].events.map(event => event.step)).toEqual([1, 2, 3])
})

for (const count of [0, 6, 7, 10]) {
  test(`exploration and finishing at ${count} reveal no correctness and emit one immutable completion`, async ({ page }) => {
    await page.goto('/tests/e2e/harness.html')
    const equation = page.getByTestId('equation')
    const initialEquation = await equation.textContent()
    const initialAreaClass = await page.getByTestId('answer-area').getAttribute('class')
    for (let i = 0; i < count; i++) await page.getByRole('button', { name: 'Agregar un bloque' }).click()
    await expectCount(page, count)
    await expect(page.getByRole('status')).toHaveText('A tu ritmo.')
    await expect(equation).toHaveText(initialEquation!)
    await expect(page.getByTestId('answer-area')).toHaveAttribute('class', initialAreaClass!)
    await expect(page.getByText(/correcto|incorrecto|bien hecho|error|te faltan/i)).toHaveCount(0)
    await page.getByRole('button', { name: 'Listo' }).dblclick()
    await expect(page.getByRole('status')).toHaveText('Terminaste')
    await expect(page.getByRole('button', { name: 'Agregar un bloque' })).toBeDisabled()
    await dragBlock(page, page.getByTestId(count === 10 ? 'answer-block' : 'reserve-block').first(), page.getByTestId(count === 10 ? 'reserve-area' : 'answer-area'))
    await expectCount(page, count)
    const evidence = await page.evaluate(() => window.screenEvidence)
    expect(evidence.completions).toHaveLength(1)
    expect(evidence.frozen).toBe(true)
    expect(evidence.completions[0].initialValue).toBe(0)
    expect(evidence.completions[0].events).toHaveLength(count + 1)
    expect(evidence.completions[0].events.at(-1)).toMatchObject({ kind: 'finishAttempt', step: count + 1 })
    expect(evidence.completions[0].events.every(event => /^\d{4}-\d\d-\d\dT.*Z$/.test(event.occurredAt))).toBe(true)
    await page.getByRole('button', { name: 'Intentar de nuevo' }).click()
    await expectCount(page, 0)
    expect(await page.evaluate(() => window.screenEvidence.resets)).toBe(1)
    await page.getByRole('button', { name: 'Listo' }).click()
    await expect.poll(() => page.evaluate(() => window.screenEvidence.completions.length)).toBe(2)
  })
}

for (const [presentation, equation] of [
  ['TOTAL_EQUALS_KNOWN_PLUS_MISSING', '18=12+□'],
  ['MISSING_PLUS_KNOWN_EQUALS_TOTAL', '□+12=18'],
]) {
  test(`honors the existing ${presentation} presentation`, async ({ page }) => {
    await page.goto(`/tests/e2e/harness.html?presentation=${presentation}`)
    await expect(page.getByTestId('equation')).toHaveText(equation)
  })
}

test('mobile touch can drag in both directions, cancel a gesture, and use tap alternatives', async ({ browser }) => {
  const context = await browser.newContext({ viewport: { width: 360, height: 1000 }, isMobile: true, hasTouch: true })
  const page = await context.newPage()
  await page.goto('http://127.0.0.1:5173/tests/e2e/harness.html')
  const session = await context.newCDPSession(page)
  async function touchDrag(source: Locator, target: Locator, cancel = false) {
    await source.scrollIntoViewIfNeeded()
    const start = await center(source)
    const end = await center(target)
    await session.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [start] })
    for (let step = 1; step <= 5; step++) {
      await session.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: start.x + (end.x - start.x) * step / 5, y: start.y + (end.y - start.y) * step / 5 }] })
    }
    await session.send('Input.dispatchTouchEvent', { type: cancel ? 'touchCancel' : 'touchEnd', touchPoints: [] })
  }
  await touchDrag(page.getByTestId('reserve-block').first(), page.getByTestId('answer-area'))
  await expectCount(page, 1)
  await touchDrag(page.getByTestId('answer-block').first(), page.getByTestId('reserve-area'))
  await expectCount(page, 0)
  await touchDrag(page.getByTestId('reserve-block').first(), page.getByTestId('answer-area'), true)
  await expectCount(page, 0)
  await page.getByRole('button', { name: 'Agregar un bloque' }).tap()
  await expectCount(page, 1)
  await page.getByRole('button', { name: 'Quitar un bloque' }).tap()
  await expectCount(page, 0)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await context.close()
})

test('double-clicking reset starts a new attempt without finishing it', async ({ page }) => {
  await page.goto('/tests/e2e/harness.html')
  await page.getByRole('button', { name: 'Agregar un bloque' }).click()
  await page.getByRole('button', { name: 'Listo' }).click()
  await page.waitForTimeout(500)
  await page.getByRole('button', { name: 'Intentar de nuevo' }).dblclick()
  await expectCount(page, 0)
  await expect(page.getByRole('status')).toHaveText('A tu ritmo.')
  const evidence = await page.evaluate(() => window.screenEvidence)
  expect(evidence.completions).toHaveLength(1)
  expect(evidence.resets).toBe(1)
})

test('rapid taps on reset are accepted once the accidental-repeat window passes', async ({ page }) => {
  await page.goto('/tests/e2e/harness.html')
  const finish = page.getByRole('button', { name: 'Listo' })
  const { x, y } = await center(finish)
  // Within the OS multi-click interval each tap reports a higher click detail.
  async function tap(clickCount: number) {
    await page.mouse.down({ clickCount })
    await page.mouse.up({ clickCount })
  }
  await page.mouse.move(x, y)
  await tap(1)
  await expect(page.getByRole('status')).toHaveText('Terminaste')
  await tap(2)
  await expect(page.getByRole('status')).toHaveText('Terminaste')
  await page.waitForTimeout(450)
  await tap(3)
  await expectCount(page, 0)
  await expect(page.getByRole('status')).toHaveText('A tu ritmo.')
  const evidence = await page.evaluate(() => window.screenEvidence)
  expect(evidence.completions).toHaveLength(1)
  expect(evidence.resets).toBe(1)
})

test('quantity buttons keep keyboard focus at both limits', async ({ page }) => {
  await page.goto('/tests/e2e/harness.html')
  const add = page.getByRole('button', { name: 'Agregar un bloque' })
  const remove = page.getByRole('button', { name: 'Quitar un bloque' })
  await add.focus()
  for (let i = 0; i < 11; i++) await page.keyboard.press('Enter')
  await expectCount(page, 10)
  await expect(add).toBeDisabled()
  await expect(add).toBeFocused()
  await remove.focus()
  for (let i = 0; i < 11; i++) await page.keyboard.press('Space')
  await expectCount(page, 0)
  await expect(remove).toBeDisabled()
  await expect(remove).toBeFocused()
  await page.getByRole('button', { name: 'Listo' }).click()
  const evidence = await page.evaluate(() => window.screenEvidence)
  expect(evidence.completions[0].events).toHaveLength(21)
})

test('equation and drop areas expose their accessible names', async ({ page }) => {
  await page.goto('/tests/e2e/harness.html')
  await expect(page.getByRole('group', { name: 'Completá la igualdad' })).toHaveText('12+□=18')
  await expect(page.getByRole('group', { name: 'Área de respuesta' })).toBeVisible()
  await expect(page.getByRole('group', { name: 'Reserva de bloques' })).toBeVisible()
})

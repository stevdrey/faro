// SPDX-License-Identifier: AGPL-3.0-or-later
import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import type {} from './harness.tsx'

const harness = '/tests/e2e/harness.html?activity=app'
const activities = [
  { name: 'Bloques', exerciseId: 'missing-addend-known-first-18-12', version: 1, reset: 'Intentar de nuevo',
    moves: ['Agregar un bloque', 'Agregar un bloque', 'Quitar un bloque'],
    kinds: ['placeBlock', 'placeBlock', 'removeBlock'] },
  { name: 'Secuencias', exerciseId: 'sequence-30-28', version: 1, reset: 'Empezar de nuevo',
    moves: ['Saltar 1 a la izquierda', 'Saltar 1 a la derecha', 'Saltar 1 a la izquierda'],
    kinds: ['jumpLeft', 'jumpRight', 'jumpLeft'] },
  { name: 'Restas', exerciseId: 'subtraction-53-28', version: 2, reset: 'Empezar de nuevo',
    moves: ['Cambiar 1 decena por 10 unidades', 'Quitar 1 unidad', 'Devolver 1 unidad'],
    kinds: ['decomposeTen', 'removeBlock', 'placeBlock'] },
] as const
const button = (page: Page, name: string) => page.getByRole('button', { name, exact: true })

async function expectCurrent(page: Page, exerciseId: string | null) {
  await expect.poll(() => page.evaluate(() => window.attemptEvidence.current?.exerciseId ?? null)).toBe(exerciseId)
}

/** Lets React commit pending updates before asserting that nothing changed. */
async function settle(page: Page) {
  await page.evaluate(() => new Promise<void>(resolve =>
    requestAnimationFrame(() => requestAnimationFrame(() => setTimeout(resolve)))))
}

async function holdAndRelease(page: Page, key: string) {
  try {
    await page.keyboard.down(key)
    for (let repeat = 0; repeat < 3; repeat++) await page.keyboard.down(key)
  } finally {
    await page.keyboard.up(key)
  }
}

for (const activity of activities) {
  for (const corrected of [false, true]) {
    test(`${activity.name}: ${corrected ? 'corrected exploration' : 'zero moves'} delivers one immutable snapshot through the app controller`, async ({ page }) => {
      await page.goto(harness)
      await button(page, activity.name).click()
      for (const move of corrected ? activity.moves : []) await button(page, move).click()
      await expectCurrent(page, null)
      await expect(page.getByText(/correcto|incorrecto|bien hecho|te faltan|error/i)).toHaveCount(0)
      await button(page, 'Listo').dblclick()
      await expect(page.getByRole('status')).toHaveText('Terminaste')
      await expect(button(page, activity.reset)).toBeFocused()
      await expectCurrent(page, activity.exerciseId)
      const evidence = await page.evaluate(() => window.attemptEvidence)
      expect(evidence.deliveries).toHaveLength(1)
      expect(evidence.current?.trace.schemaVersion).toBe(activity.version)
      const events = evidence.current!.trace.events
      expect(events.map(event => event.kind)).toEqual([...(corrected ? activity.kinds : []), 'finishAttempt'])
      expect(events.map(event => event.step)).toEqual(events.map((_, index) => index + 1))
      for (const event of events) expect(event.occurredAt).toMatch(/^\d{4}-\d{2}-\d{2}T.*Z$/)
      expect(await page.evaluate(() => {
        const attempt = window.attemptEvidence.current!
        let rejectsMutation = false
        try { Object.assign(attempt.trace.events[0], { step: 999 }) } catch (error) { rejectsMutation = error instanceof TypeError }
        return Object.isFrozen(attempt) && Object.isFrozen(attempt.trace)
          && Object.isFrozen(attempt.trace.events) && attempt.trace.events.every(Object.isFrozen) && rejectsMutation
      })).toBe(true)
      for (const move of new Set(activity.moves)) {
        await expect(button(page, move)).toBeDisabled()
        await button(page, move).dispatchEvent('click')
      }
      // Re-delivery and reselecting the active activity must preserve the same object.
      await page.evaluate(() => window.redeliverCompletion())
      await settle(page)
      await button(page, activity.name).click()
      await settle(page)
      expect(await page.evaluate(() => window.attemptEvidence.current === window.attemptEvidence.deliveries[0])).toBe(true)
      expect(await page.evaluate(() => {
        const { current, deliveries } = window.attemptEvidence
        return { current, deliveries }
      })).toEqual({ current: evidence.current, deliveries: evidence.deliveries })
      await button(page, activity.reset).dblclick()
      await expectCurrent(page, null)
      await expect(button(page, 'Listo')).toBeEnabled()
      await button(page, 'Listo').click()
      await expect.poll(() => page.evaluate(() => window.attemptEvidence.deliveries.length)).toBe(2)
      const restarted = await page.evaluate(() => window.attemptEvidence)
      expect(restarted.deliveries[0]).toEqual(evidence.current)
      expect(restarted.current?.trace.events).toEqual([expect.objectContaining({ kind: 'finishAttempt', step: 1 })])
    })
  }

  for (const key of ['Enter', 'Space']) {
    test(`${activity.name}: held ${key} completes once and does not cross finish/reset transitions`, async ({ page }) => {
      await page.goto(harness)
      await button(page, activity.name).click()
      await button(page, 'Listo').focus()
      await holdAndRelease(page, key)
      await expectCurrent(page, activity.exerciseId)
      await expect(button(page, activity.reset)).toBeFocused()
      expect(await page.evaluate(() => window.attemptEvidence.deliveries.length)).toBe(1)
      await holdAndRelease(page, key)
      await expectCurrent(page, null)
      await button(page, 'Listo').focus()
      await page.keyboard.press(key)
      await expect.poll(() => page.evaluate(() => window.attemptEvidence.deliveries.length)).toBe(2)
    })
  }

  test(`${activity.name}: changing activities discards completed and unfinished attempts`, async ({ page }) => {
    await page.goto(harness)
    await button(page, activity.name).click()
    await button(page, 'Listo').click()
    await expectCurrent(page, activity.exerciseId)
    const other = activities.find(candidate => candidate.name !== activity.name)!
    await button(page, other.name).click()
    await expectCurrent(page, null)
    await button(page, activity.name).click()
    await expect(button(page, 'Listo')).toBeEnabled()
    await button(page, activity.moves[0]).click()
    await button(page, other.name).click()
    await button(page, activity.name).click()
    await button(page, 'Listo').click()
    await expectCurrent(page, activity.exerciseId)
    expect(await page.evaluate(() => window.attemptEvidence.current!.trace.events.map(event => event.kind))).toEqual(['finishAttempt'])
  })
}

for (const count of [6, 7, 10]) {
  test(`blocks: ${count} pieces have the same neutral completion and remain unchanged until Listo`, async ({ page }) => {
    await page.goto(harness)
    for (let index = 0; index < count; index++) await button(page, 'Agregar un bloque').click()
    await expectCurrent(page, null)
    await expect(page.getByRole('status')).toHaveText('A tu ritmo.')
    await expect(page.getByText(/correcto|incorrecto|bien hecho|te faltan|error/i)).toHaveCount(0)
    await button(page, 'Listo').click()
    await expect(page.getByRole('status')).toHaveText('Terminaste')
    await expectCurrent(page, activities[0].exerciseId)
    expect(await page.evaluate(() => window.attemptEvidence.current!.trace.events.length)).toBe(count + 1)
  })
}

test('changing sequence examples releases the snapshot and remembers only the chosen example', async ({ page }) => {
  await page.goto(harness)
  await button(page, 'Secuencias').click()
  await button(page, 'Listo').click()
  await expectCurrent(page, 'sequence-30-28')
  const example = page.getByLabel('Elegí una secuencia')
  await example.selectOption('descending')
  await expectCurrent(page, 'sequence-30-28')
  await example.selectOption('ascending')
  await expectCurrent(page, null)
  await button(page, 'Saltar 1 a la derecha').click()
  await example.selectOption('descending')
  await example.selectOption('ascending')
  await button(page, 'Listo').click()
  await expectCurrent(page, 'sequence-10-12')
  expect(await page.evaluate(() => window.attemptEvidence.current!.trace)).toMatchObject({
    initialValue: 12, events: [{ kind: 'finishAttempt', step: 1 }],
  })
  await button(page, 'Bloques').click()
  await button(page, 'Secuencias').click()
  await expect(example).toHaveValue('ascending')
  await expectCurrent(page, null)
})

test('reload forgets the controller and completing all activities writes no storage or API requests', async ({ page }) => {
  const requests: string[] = []
  page.on('request', request => { if (/\/api\/|\/actuator\//.test(request.url())) requests.push(request.url()) })
  await page.goto(harness)
  for (const activity of activities) {
    await button(page, activity.name).click()
    await button(page, 'Listo').click()
    await expectCurrent(page, activity.exerciseId)
  }
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expect(requests).toEqual([])
  await page.reload()
  // Wait for the remounted controller to commit, so a rehydrated snapshot would be visible.
  await expect.poll(() => page.evaluate(() => window.attemptEvidence.effects)).toBeGreaterThan(0)
  await expect(page.getByRole('status')).toHaveText('A tu ritmo.')
  await settle(page)
  expect(await page.evaluate(() => {
    const { current, deliveries } = window.attemptEvidence
    return { current, deliveries, storage: [localStorage.length, sessionStorage.length] }
  })).toEqual({ current: null, deliveries: [], storage: [0, 0] })
  await expect(button(page, 'Bloques')).toHaveAttribute('aria-pressed', 'true')
  await expect(button(page, 'Listo')).toBeEnabled()
})

test.describe('touch integration', () => {
  test.use({ hasTouch: true, viewport: { width: 360, height: 1000 } })
  test('one tap completes each activity and reset clears the snapshot', async ({ page }) => {
    await page.goto(harness)
    for (const activity of activities) {
      await button(page, activity.name).tap()
      await button(page, activity.moves[0]).tap()
      await button(page, 'Listo').tap()
      await expectCurrent(page, activity.exerciseId)
      await expect(page.getByRole('status')).toHaveText('Terminaste')
      await button(page, activity.reset).tap()
      await expectCurrent(page, null)
    }
  })
})

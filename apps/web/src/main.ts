// SPDX-License-Identifier: AGPL-3.0-or-later
import './styles.css'

const app = document.querySelector<HTMLElement>('#app')
if (!app) throw new Error('Missing application root')

app.innerHTML = `
  <section class="shell" aria-labelledby="page-title">
    <p class="eyebrow">PROYECTO FARO · MVP 0.1</p>
    <h1 id="page-title">Explorá las matemáticas a tu manera</h1>
    <p class="lede">Estamos preparando ejercicios con objetos y explicaciones habladas. Esta es una pantalla inicial, todavía sin actividades.</p>
    <div class="panel">
      <h2>Estado del servidor</h2>
      <p role="status" aria-live="polite" id="api-status">Comprobando…</p>
    </div>
    <p class="notice">Esta versión no graba audio ni recopila información infantil.</p>
  </section>
`

const statusElement = document.querySelector<HTMLElement>('#api-status')
const controller = new AbortController()
const timeout = setTimeout(() => controller.abort(), 4000)

void fetch('/actuator/health', { signal: controller.signal })
  .then(async (response) => {
    if (!response.ok) throw new Error('API unreachable')
    const payload: unknown = await response.json()
    return typeof payload === 'object' && payload !== null && 'status' in payload && payload.status === 'UP'
  })
  .then((healthy) => {
    if (statusElement) statusElement.textContent = healthy ? 'Conectado (UP)' : 'Respuesta inesperada'
  })
  .catch(() => {
    if (statusElement) statusElement.textContent = 'No hay conexión. Iniciá el servidor con Gradle.'
  })
  .finally(() => clearTimeout(timeout))

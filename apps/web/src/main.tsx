// SPDX-License-Identifier: AGPL-3.0-or-later
import { StrictMode, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import './styles.css'

function App() {
  const [apiStatus, setApiStatus] = useState('Comprobando…')

  useEffect(() => {
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 4_000)

    async function checkHealth() {
      try {
        const response = await fetch('/actuator/health', { signal: controller.signal })
        if (!response.ok) throw new Error('API unreachable')
        const payload: unknown = await response.json()
        setApiStatus(
          typeof payload === 'object' && payload !== null &&
            'status' in payload && payload.status === 'UP'
            ? 'Conectado (UP)'
            : 'Respuesta inesperada',
        )
      } catch {
        if (!controller.signal.aborted) {
          setApiStatus('No hay conexión. Iniciá el servidor con Gradle.')
        }
      } finally {
        clearTimeout(timeout)
      }
    }

    void checkHealth()
    return () => {
      clearTimeout(timeout)
      controller.abort()
    }
  }, [])

  return (
    <section className="shell" aria-labelledby="page-title">
      <p className="eyebrow">PROYECTO FARO · MVP 0.1</p>
      <h1 id="page-title">Explorá las matemáticas a tu manera</h1>
      <p className="lede">
        Estamos preparando ejercicios con objetos y explicaciones habladas.
        Esta es una pantalla inicial, todavía sin actividades.
      </p>
      <div className="panel">
        <h2>Estado del servidor</h2>
        <p role="status" aria-live="polite">{apiStatus}</p>
      </div>
      <p className="notice">Esta versión no graba audio ni recopila información infantil.</p>
    </section>
  )
}

const root = document.getElementById('app')
if (!root) throw new Error('Missing application root')
createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

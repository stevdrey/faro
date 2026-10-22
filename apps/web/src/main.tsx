// SPDX-License-Identifier: AGPL-3.0-or-later
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './App.tsx'
import './styles.css'

const root = document.getElementById('app')
if (!root) throw new Error('Missing application root')
createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
)

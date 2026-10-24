// SPDX-License-Identifier: AGPL-3.0-or-later
import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  forbidOnly: Boolean(process.env.CI),
  retries: 0,
  workers: 1,
  reporter: 'list',
  use: {
    browserName: 'chromium',
    baseURL: 'http://127.0.0.1:5173',
    viewport: { width: 1100, height: 1000 },
    locale: 'es-CR',
    // Synthetic runs only; keep captured artifacts off by default.
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  webServer: [{
    // The default build: the synthetic ASR preview stays disabled.
    command: 'npm run dev',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: !process.env.CI,
  }, {
    // Only transcription.spec.ts uses this opt-in synthetic preview server.
    command: 'npm run dev -- --port 5174',
    env: { VITE_FARO_SYNTHETIC_ASR: 'true' },
    url: 'http://127.0.0.1:5174',
    // Own the server so the build-time preview flag is deterministic.
    reuseExistingServer: false,
  }],
})

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
  webServer: {
    command: 'npm run dev',
    // Exercises the opt-in synthetic preview; normal dev/build commands keep it disabled.
    env: { VITE_FARO_SYNTHETIC_ASR: 'true' },
    url: 'http://127.0.0.1:5173',
    // Own the server so the compile-time synthetic preview flag is deterministic.
    reuseExistingServer: false,
  },
})

import { defineConfig } from '@playwright/test';

const baseURL = process.env.E2E_BASE_URL || 'http://localhost:5174';

export default defineConfig({
  testDir: 'test/playwright',
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL,
    trace: 'off',
  },
});

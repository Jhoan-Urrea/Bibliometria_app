import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: 'http://127.0.0.1:8080',
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: {
    command:
      'mvn --batch-mode --no-transfer-progress -pl api -am -DskipTests package && java -jar api/target/api-1.0.0-SNAPSHOT.jar --server.port=8080',
    cwd: '..',
    url: 'http://127.0.0.1:8080/api/health',
    timeout: 180_000,
    reuseExistingServer: false,
  },
});

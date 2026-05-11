import { test, expect } from '@playwright/test';

test('根路径 HTTP 200（需已启动服务并设置 E2E_BASE_URL，不依赖本机浏览器安装）', async ({
  request,
}) => {
  test.skip(!process.env.E2E_BASE_URL, '请设置 E2E_BASE_URL，例如 http://127.0.0.1:3000');
  const res = await request.get('/');
  expect(res.ok()).toBeTruthy();
});

# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: devteam-ui.spec.ts >> dev-team 阶段 C UI >> A1 员工 侧栏与关键路由
- Location: test\playwright\devteam-ui.spec.ts:48:9

# Error details

```
Test timeout of 60000ms exceeded.
```

```
Error: locator.fill: Test timeout of 60000ms exceeded.
Call log:
  - waiting for locator('input[autocomplete="username"]')

```

# Test source

```ts
  1  | import { expect, test, type Page } from '@playwright/test';
  2  | 
  3  | const password = process.env.DEVTEAM_PASSWORD || '123456';
  4  | 
  5  | const personas: Array<{ id: string; label: string; paths: string[] }> = [
  6  |   {
  7  |     id: 'zhou_rong',
  8  |     label: 'E 超管',
  9  |     paths: ['/', '/todo', '/performances', '/admin/templates', '/admin/employees', '/admin/permissions'],
  10 |   },
  11 |   {
  12 |     id: 'qa_admin',
  13 |     label: 'D 管理员',
  14 |     paths: ['/', '/todo', '/performances', '/admin/templates', '/admin/employees'],
  15 |   },
  16 |   {
  17 |     id: 'demo_manager',
  18 |     label: 'B 直属上级',
  19 |     paths: ['/', '/todo', '/performances', '/my-performance'],
  20 |   },
  21 |   {
  22 |     id: 'demo_dotted_mgr',
  23 |     label: 'C 虚线上级',
  24 |     paths: ['/', '/todo', '/performances'],
  25 |   },
  26 |   {
  27 |     id: 'demo_emp_01',
  28 |     label: 'A1 员工',
  29 |     paths: ['/', '/todo', '/my-performance'],
  30 |   },
  31 |   {
  32 |     id: 'demo_emp_02',
  33 |     label: 'A2 员工',
  34 |     paths: ['/', '/todo', '/my-performance'],
  35 |   },
  36 | ];
  37 | 
  38 | async function login(page: Page, username: string) {
  39 |   await page.goto('/login');
> 40 |   await page.locator('input[autocomplete="username"]').fill(username);
     |                                                        ^ Error: locator.fill: Test timeout of 60000ms exceeded.
  41 |   await page.locator('input[autocomplete="current-password"]').fill(password);
  42 |   await page.getByRole('button', { name: '登录', exact: true }).click();
  43 |   await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15_000 });
  44 | }
  45 | 
  46 | test.describe('dev-team 阶段 C UI', () => {
  47 |   for (const persona of personas) {
  48 |     test(`${persona.label} 侧栏与关键路由`, async ({ page }) => {
  49 |       const consoleErrors: string[] = [];
  50 |       const pageErrors: string[] = [];
  51 |       page.on('console', (msg) => {
  52 |         if (msg.type() === 'error') consoleErrors.push(msg.text());
  53 |       });
  54 |       page.on('pageerror', (error) => pageErrors.push(error.message));
  55 | 
  56 |       await login(page, persona.id);
  57 |       await expect(page.locator('aside')).toBeVisible();
  58 | 
  59 |       for (const path of persona.paths) {
  60 |         await page.goto(path);
  61 |         await expect(page.locator('body')).toBeVisible();
  62 |         await expect(page.getByText('账号登录')).toHaveCount(0);
  63 |       }
  64 | 
  65 |       expect(pageErrors, pageErrors.join('\n')).toEqual([]);
  66 |       expect(
  67 |         consoleErrors.filter((line) => !line.includes('favicon')),
  68 |         consoleErrors.join('\n'),
  69 |       ).toEqual([]);
  70 |     });
  71 |   }
  72 | });
  73 | 
```
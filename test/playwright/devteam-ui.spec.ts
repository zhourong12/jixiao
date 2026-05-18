import { expect, test, type Page } from '@playwright/test';

const password = process.env.DEVTEAM_PASSWORD || '123456';

const personas: Array<{ id: string; label: string; paths: string[] }> = [
  {
    id: 'zhou_rong',
    label: 'E 超管',
    paths: ['/', '/todo', '/performances', '/admin/templates', '/admin/employees', '/admin/permissions'],
  },
  {
    id: 'qa_admin',
    label: 'D 管理员',
    paths: ['/', '/todo', '/performances', '/admin/templates', '/admin/employees'],
  },
  {
    id: 'demo_manager',
    label: 'B 直属上级',
    paths: ['/', '/todo', '/performances', '/my-performance'],
  },
  {
    id: 'demo_dotted_mgr',
    label: 'C 虚线上级',
    paths: ['/', '/todo', '/performances'],
  },
  {
    id: 'demo_emp_01',
    label: 'A1 员工',
    paths: ['/', '/todo', '/my-performance'],
  },
  {
    id: 'demo_emp_02',
    label: 'A2 员工',
    paths: ['/', '/todo', '/my-performance'],
  },
];

async function login(page: Page, username: string) {
  await page.goto('/login');
  await page.locator('input[autocomplete="username"]').fill(username);
  await page.locator('input[autocomplete="current-password"]').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 15_000 });
}

test.describe('dev-team 阶段 C UI', () => {
  for (const persona of personas) {
    test(`${persona.label} 侧栏与关键路由`, async ({ page }) => {
      const consoleErrors: string[] = [];
      const pageErrors: string[] = [];
      page.on('console', (msg) => {
        if (msg.type() === 'error') consoleErrors.push(msg.text());
      });
      page.on('pageerror', (error) => pageErrors.push(error.message));

      await login(page, persona.id);
      await expect(page.locator('aside')).toBeVisible();

      for (const path of persona.paths) {
        await page.goto(path);
        await expect(page.locator('body')).toBeVisible();
        await expect(page.getByText('账号登录')).toHaveCount(0);
      }

      expect(pageErrors, pageErrors.join('\n')).toEqual([]);
      expect(
        consoleErrors.filter((line) => !line.includes('favicon')),
        consoleErrors.join('\n'),
      ).toEqual([]);
    });
  }
});

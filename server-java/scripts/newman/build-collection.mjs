/**
 * 生成 Postman Collection v2.1
 * 用法: node scripts/newman/build-collection.mjs
 */
import fs from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __dirname = dirname(fileURLToPath(import.meta.url));

const cookieH = { key: 'Cookie', value: 'jx_session={{jx_session}}' };
const jsonH = { key: 'Content-Type', value: 'application/json' };

/** 字符串 URL 便于 Newman 解析；query 拼进 raw */
function url(path, query) {
  const p = path.startsWith('/') ? path.slice(1) : path;
  let s = `{{baseUrl}}/${p}`;
  if (query?.length) {
    const qs = query
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
      .join('&');
    s += `?${qs}`;
  }
  return s;
}

function req(name, method, path, opts = {}) {
  const headers = [...(opts.noAuth ? [] : [cookieH]), ...(opts.json || opts.body !== undefined ? [jsonH] : [])];
  const item = {
    name,
    request: {
      method,
      header: headers,
      url: url(path, opts.query),
    },
  };
  if (opts.body !== undefined) {
    item.request.body = { mode: 'raw', raw: JSON.stringify(opts.body, null, 2) };
  }
  if (opts.events) item.event = opts.events;
  return item;
}

const loginTests = {
  listen: 'test',
  script: {
    exec: [
      'const c = pm.response && pm.response.code;',
      'if (c == null || c < 200 || c >= 300) return;',
      'pm.collectionVariables.unset("jx_session");',
      'const rows = pm.response.headers.all ? pm.response.headers.all() : [];',
      'for (const h of rows) {',
      '  if (String(h.key).toLowerCase() !== "set-cookie") continue;',
      '  const v = String(h.value);',
      '  const m = v.match(/jx_session=([^;]+)/);',
      '  if (m) { pm.collectionVariables.set("jx_session", decodeURIComponent(m[1])); break; }',
      '}',
      'pm.test("HTTP 2xx", function () { pm.expect(pm.response.code).to.be.within(200, 299); });',
    ],
    type: 'text/javascript',
  },
};

const savePerfIdTests = {
  listen: 'test',
  script: {
    exec: [
      'try {',
      '  const j = pm.response.json();',
      '  if (j.items && j.items.length) pm.collectionVariables.set("performance_id", j.items[0].id);',
      '} catch (e) {}',
    ],
    type: 'text/javascript',
  },
};

const DEMO_GOALS = [
  { indicatorName: '业绩目标', target: 'T1', weight: 40 },
  { indicatorName: '团队协作', target: 'T2', weight: 30 },
  { indicatorName: '专业能力', target: 'T3', weight: 30 },
];

const DEMO_REVIEW = [
  { indicatorName: '业绩目标', score: 4, comment: 'n' },
  { indicatorName: '团队协作', score: 4, comment: 'n' },
  { indicatorName: '专业能力', score: 4, comment: 'n' },
];

const collection = {
  info: {
    name: 'jixiao2 API（全量）',
    description:
      '由 scripts/newman/build-collection.mjs 生成。登录首请求写入 jx_session。performance_id 从列表接口自动取第一条（若有）。\n\n用 agent-browser 录制真实参数：见 scripts/newman/capture-with-agent-browser.md',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
  },
  variable: [
    { key: 'baseUrl', value: 'http://127.0.0.1:3000' },
    { key: 'jx_session', value: '' },
    { key: 'performance_id', value: '' },
    { key: 'template_id', value: 'tpl-demo-001' },
    { key: 'employee_id', value: 'demo_emp_01' },
    { key: 'evaluation_period_id', value: '' },
    { key: 'award_id', value: '' },
  ],
  item: [
    {
      name: '00 Auth',
      item: [
        {
          name: '01 Login password',
          event: [loginTests],
          request: {
            method: 'POST',
            header: [jsonH],
            body: {
              mode: 'raw',
              raw: JSON.stringify(
                { username: '{{login_user}}', password: '{{login_password}}' },
                null,
                2,
              ),
            },
            url: url('auth/password/login', undefined),
          },
        },
      ],
    },
    {
      name: '01 Session',
      item: [
        req('GET session me', 'GET', 'api/session/me'),
        req('POST session logout', 'POST', 'api/session/logout', { json: true, body: {} }),
      ],
    },
    {
      name: '02 Home',
      item: [
        req('GET home todos', 'GET', 'api/home/todos', {
          query: [
            ['year', '2026'],
            ['month', '5'],
          ],
        }),
        req('GET home overview', 'GET', 'api/home/overview', {
          query: [
            ['year', '2026'],
            ['month', '5'],
          ],
        }),
        req('GET home action-counts', 'GET', 'api/home/action-counts', {
          query: [
            ['year', '2026'],
            ['month', '5'],
          ],
        }),
      ],
    },
    {
      name: '03 Menu permissions',
      item: [
        req('GET menu-permissions me', 'GET', 'api/menu-permissions/me'),
        req('GET admin menu matrix', 'GET', 'api/admin/menu-permissions/matrix'),
        req('PUT admin menu matrix', 'PUT', 'api/admin/menu-permissions', {
          json: true,
          body: { role: 'employee', menus: { todo: true } },
        }),
        req('GET admin rbac roles', 'GET', 'api/admin/menu-permissions/roles'),
        req('POST admin rbac role', 'POST', 'api/admin/menu-permissions/roles', {
          json: true,
          body: { roleKey: 'newman_demo_role', name: 'Newman演示角色', sortOrder: 99 },
        }),
        req('PATCH admin rbac role', 'PATCH', 'api/admin/menu-permissions/roles/newman_demo_role', {
          json: true,
          body: { name: 'Newman演示角色改名', sortOrder: 100 },
        }),
        req('DELETE admin rbac role', 'DELETE', 'api/admin/menu-permissions/roles/newman_demo_role'),
      ],
    },
    {
      name: '04 Employees',
      item: [
        req('GET employees', 'GET', 'api/employees', {
          query: [
            ['page', '1'],
            ['pageSize', '20'],
          ],
        }),
        req('GET employees departments', 'GET', 'api/employees/departments'),
        req('GET employees department-options', 'GET', 'api/employees/department-options'),
        req('GET employees role-options', 'GET', 'api/employees/role-options'),
        req('GET employees all', 'GET', 'api/employees/all'),
        req('POST employees', 'POST', 'api/employees', {
          json: true,
          body: {
            userId: '{{newman_employee_user_id}}',
            name: 'Newman测试用户',
            departmentName: '管理部',
            managerId: 'demo_manager',
          },
        }),
        req('PUT employees hierarchy', 'PUT', 'api/employees/{{newman_employee_user_id}}/hierarchy', {
          json: true,
          body: { managerId: 'demo_manager', departmentName: '管理部' },
        }),
        req('PUT employees', 'PUT', 'api/employees/{{newman_employee_user_id}}', {
          json: true,
          body: { name: 'Newman测试用户改名' },
        }),
        req('DELETE employees', 'DELETE', 'api/employees/{{newman_employee_user_id}}'),
        req('POST employees sync-from-lark', 'POST', 'api/employees/sync-from-lark', {
          json: true,
          body: { clearExisting: false },
        }),
      ],
    },
    {
      name: '05 Performance',
      item: [
        req('GET performances list', 'GET', 'api/performances', {
          query: [
            ['page', '1'],
            ['pageSize', '20'],
          ],
          events: [savePerfIdTests],
        }),
        req('GET performances month-periods', 'GET', 'api/performances/create/month-periods'),
        req('GET performances export', 'GET', 'api/performances/export', {
          query: [['period', '2026-01']],
        }),
        req('GET performances calibration queue', 'GET', 'api/performances/calibration/supervisor-queue', {
          query: [
            ['page', '1'],
            ['pageSize', '10'],
          ],
        }),
        req('GET performances by id', 'GET', 'api/performances/{{performance_id}}'),
        req('POST performances batch create', 'POST', 'api/performances', {
          json: true,
          body: {
            employeeNames: ['张三'],
            period: '2099-12-newman',
          },
        }),
        req('POST performances select-template', 'POST', 'api/performances/{{performance_id}}/select-template', {
          json: true,
          body: { templateId: '{{template_id}}' },
        }),
        req('PATCH performances draft', 'PATCH', 'api/performances/{{performance_id}}', {
          json: true,
          body: { reviewType: 'goal', content: DEMO_GOALS },
        }),
        req('POST performances submit goal', 'POST', 'api/performances/{{performance_id}}/submit', {
          json: true,
          body: { reviewType: 'goal', content: DEMO_GOALS },
        }),
        req('POST performances approve-goal', 'POST', 'api/performances/{{performance_id}}/approve-goal', {
          json: true,
          body: { approved: true },
        }),
        req('POST performances submit self', 'POST', 'api/performances/{{performance_id}}/submit', {
          json: true,
          body: { reviewType: 'self', content: DEMO_REVIEW, personalSummary: 'newman' },
        }),
        req('POST performances submit manager', 'POST', 'api/performances/{{performance_id}}/submit', {
          json: true,
          body: { reviewType: 'manager', content: DEMO_REVIEW },
        }),
        req('POST performances submit dotted', 'POST', 'api/performances/{{performance_id}}/submit', {
          json: true,
          body: { reviewType: 'dotted_manager', content: DEMO_REVIEW },
        }),
        req('POST performances reject', 'POST', 'api/performances/{{performance_id}}/reject', {
          json: true,
          body: { reason: 'newman' },
        }),
        req('POST performances final-review', 'POST', 'api/performances/{{performance_id}}/final-review', {
          json: true,
          body: { approved: true },
        }),
        req('POST performances calibrate', 'POST', 'api/performances/{{performance_id}}/calibrate', {
          json: true,
          body: { approved: true, finalScore: 4.2 },
        }),
      ],
    },
    {
      name: '06 Templates',
      item: [
        req('GET templates list', 'GET', 'api/admin/templates', {
          noAuth: true,
          query: [
            ['page', '1'],
            ['pageSize', '20'],
          ],
        }),
        req('GET templates by id', 'GET', 'api/admin/templates/{{template_id}}', { noAuth: true }),
        req('POST templates', 'POST', 'api/admin/templates', {
          json: true,
          body: {
            name: 'Newman模板',
            position: '员工',
            indicators: [
              { name: '指标A', weight: 50, description: 'd' },
              { name: '指标B', weight: 50, description: 'd' },
            ],
          },
        }),
        req('PATCH templates', 'PATCH', 'api/admin/templates/{{template_id}}', {
          json: true,
          body: { name: '通用绩效考核模板' },
        }),
        req('POST templates toggle-status', 'POST', 'api/admin/templates/{{template_id}}/toggle-status'),
        req('POST templates copy', 'POST', 'api/admin/templates/{{template_id}}/copy'),
      ],
    },
    {
      name: '07 Notifications',
      item: [
        req('GET notifications', 'GET', 'api/admin/notifications', {
          noAuth: true,
          query: [
            ['page', '1'],
            ['pageSize', '20'],
          ],
        }),
        req('POST notifications send', 'POST', 'api/admin/notifications', {
          json: true,
          body: {
            title: 'Newman通知',
            content: '内容',
            sendType: 'specified',
            targetIds: ['zhou_rong'],
          },
        }),
      ],
    },
    {
      name: '08 System config',
      item: [
        req('GET system-config', 'GET', 'api/admin/system-config'),
        req('PATCH system-config', 'PATCH', 'api/admin/system-config', {
          json: true,
          body: { configs: [{ key: 'manager_review_weight', value: '0.7' }] },
        }),
      ],
    },
    {
      name: '09 Evaluation periods',
      item: [
        req('GET evaluation-periods', 'GET', 'api/admin/evaluation-periods', {
          query: [['period_type', 'month']],
        }),
        req('POST evaluation-periods', 'POST', 'api/admin/evaluation-periods', {
          json: true,
          body: {
            periodType: 'month',
            periodKey: '2099-01',
            name: 'Newman月度',
            sortOrder: 999,
            status: 'draft',
          },
        }),
        req('PUT evaluation-periods', 'PUT', 'api/admin/evaluation-periods/{{evaluation_period_id}}', {
          json: true,
          body: { name: '改名' },
        }),
        req('DELETE evaluation-periods', 'DELETE', 'api/admin/evaluation-periods/{{evaluation_period_id}}'),
      ],
    },
    {
      name: '10 Award types',
      item: [req('GET award-types', 'GET', 'api/admin/award-types')],
    },
    {
      name: '11 Evaluation ops',
      item: [
        req('GET evaluation performance-periods', 'GET', 'api/admin/evaluation/performance-periods'),
        req('GET evaluation leaderboard', 'GET', 'api/admin/evaluation/leaderboard', {
          query: [
            ['scope', 'month'],
            ['key', '2026-01'],
          ],
        }),
        req('GET evaluation awards', 'GET', 'api/admin/evaluation/awards', {
          query: [['periodId', '{{evaluation_period_id}}']],
        }),
        req('POST evaluation awards', 'POST', 'api/admin/evaluation/awards', {
          json: true,
          body: {
            periodId: '{{evaluation_period_id}}',
            awardCode: 'STAR',
            employeeId: '{{employee_id}}',
            remark: 'newman',
          },
        }),
        req('DELETE evaluation award', 'DELETE', 'api/admin/evaluation/awards/{{award_id}}'),
      ],
    },
  ],
};

const outPath = join(__dirname, 'jixiao2-api.postman_collection.json');
fs.writeFileSync(outPath, JSON.stringify(collection, null, 2), 'utf8');
console.error('Wrote', outPath);

/**
 * 生成 Postman Collection v2.1
 * 用法: node scripts/newman/build-collection.mjs
 */
import fs from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const __dirname = dirname(fileURLToPath(import.meta.url));

const cookieH = { key: 'Cookie', value: 'jx_session={{jx_session}}' };
const bearerH = { key: 'Authorization', value: 'Bearer {{api_token}}' };
const jsonH = { key: 'Content-Type', value: 'application/json' };

/** 字符串 URL 便于 Newman 解析；query 拼进 raw */
function url(path, query) {
  const p = path.startsWith('/') ? path.slice(1) : path;
  let s = `{{baseUrl}}/${p}`;
  if (query?.length) {
    const qs = query
      .map(([k, v]) => {
        const raw = String(v);
        const encodedValue = raw.includes('{{') ? raw : encodeURIComponent(raw);
        return `${encodeURIComponent(k)}=${encodedValue}`;
      })
      .join('&');
    s += `?${qs}`;
  }
  return s;
}

const http2xxTests = {
  listen: 'test',
  script: {
    exec: [
      'pm.test("HTTP 2xx", function () { pm.expect(pm.response.code).to.be.within(200, 299); });',
    ],
    type: 'text/javascript',
  },
};

function req(name, method, path, opts = {}) {
  const headers = [
    ...(opts.noAuth ? [] : [cookieH]),
    ...(opts.bearer ? [bearerH] : []),
    ...(opts.json || opts.body !== undefined ? [jsonH] : []),
  ];
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
  const events = [...(opts.events || []), ...(opts.skipAssert ? [] : [http2xxTests])];
  if (events.length) item.event = events;
  return item;
}

const loginTests = {
  listen: 'test',
  script: {
    exec: [
      'const c = pm.response && pm.response.code;',
      'if (c == null || c < 200 || c >= 300) return;',
      'pm.collectionVariables.unset("jx_session");',
      'if (pm.environment.unset) pm.environment.unset("jx_session");',
      'let raw = pm.response.headers.get("Set-Cookie") || pm.response.headers.get("set-cookie") || "";',
      'if (!raw) {',
      '  const rows = pm.response.headers.all ? pm.response.headers.all() : [];',
      '  for (const h of rows) {',
      '    if (String(h.key).toLowerCase() !== "set-cookie") continue;',
      '    raw = String(h.value);',
      '    break;',
      '  }',
      '}',
      'const m = String(raw).match(/jx_session=([^;]+)/);',
      'if (m) {',
      '  const token = decodeURIComponent(m[1]);',
      '  pm.collectionVariables.set("jx_session", token);',
      '  if (pm.environment.set) pm.environment.set("jx_session", token);',
      '}',
      'pm.test("HTTP 2xx", function () { pm.expect(pm.response.code).to.be.within(200, 299); });',
    ],
    type: 'text/javascript',
  },
};

const setVarLines = [
  'function setVar(key, value) {',
  '  pm.collectionVariables.set(key, value);',
  '  if (pm.environment.set) pm.environment.set(key, value);',
  '}',
];

const savePerfIdTests = {
  listen: 'test',
  script: {
    exec: [
      ...setVarLines,
      'try {',
      '  const j = pm.response.json();',
      '  const items = j.items || (j.data && j.data.items) || [];',
      '  if (items.length) setVar("performance_id", items[0].id);',
      '} catch (e) {}',
    ],
    type: 'text/javascript',
  },
};

const saveEvalPeriodIdTests = {
  listen: 'test',
  script: {
    exec: [
      ...setVarLines,
      'try {',
      '  const j = pm.response.json();',
      '  const items = j.items || (j.data && j.data.items) || [];',
      '  if (items.length) setVar("evaluation_period_id", items[0].id);',
      '} catch (e) {}',
    ],
    type: 'text/javascript',
  },
};

const saveEvalPeriodIdFromCreateTests = {
  listen: 'test',
  script: {
    exec: [
      ...setVarLines,
      'try {',
      '  const j = pm.response.json();',
      '  const id = j.id || (j.data && j.data.id);',
      '  if (id) setVar("evaluation_period_id", id);',
      '} catch (e) {}',
    ],
    type: 'text/javascript',
  },
};

const assignNewmanPerfPeriodPrerequest = {
  listen: 'prerequest',
  script: {
    exec: [
      ...setVarLines,
      'const stamp = String(Date.now());',
      'setVar("newman_perf_period", `2099-nm-${stamp}`);',
    ],
    type: 'text/javascript',
  },
};

const assignNewmanEvalPeriodPrerequest = {
  listen: 'prerequest',
  script: {
    exec: [
      ...setVarLines,
      'const month = String((Date.now() % 12) + 1).padStart(2, "0");',
      'setVar("newman_eval_period_key", `2096-${month}`);',
      'const m = parseInt(month, 10);',
      'const q = Math.floor((m - 1) / 3) + 1;',
      'setVar("newman_eval_quarter_key", `2096-Q${q}`);',
    ],
    type: 'text/javascript',
  },
};

const saveNewmanEvalQuarterIdFromCreateTests = {
  listen: 'test',
  script: {
    exec: [
      ...setVarLines,
      'try {',
      '  const j = pm.response.json();',
      '  const id = j.id || (j.data && j.data.id);',
      '  if (id) setVar("newman_eval_quarter_id", id);',
      '} catch (e) {}',
    ],
    type: 'text/javascript',
  },
};

const savePerfIdFromBatchTests = {
  listen: 'test',
  script: {
    exec: [
      ...setVarLines,
      'try {',
      '  const j = pm.response.json();',
      '  const rows = j.results || (j.data && j.data.results) || [];',
      '  const hit = rows.find((r) => r && r.success && r.id && r.employeeId === "demo_emp_01");',
      '  if (hit) setVar("performance_id", hit.id);',
      '} catch (e) {}',
      'pm.test("batch create demo_emp_01", function () {',
      '  const j = pm.response.json();',
      '  pm.expect(j.successCount).to.be.above(0);',
      '});',
    ],
    type: 'text/javascript',
  },
};

const saveAwardIdTests = {
  listen: 'test',
  script: {
    exec: [
      ...setVarLines,
      'try {',
      '  const j = pm.response.json();',
      '  const id = j.id || (j.data && j.data.id);',
      '  if (id) setVar("award_id", id);',
      '} catch (e) {}',
    ],
    type: 'text/javascript',
  },
};

const saveApiTokenTests = {
  listen: 'test',
  script: {
    exec: [
      ...setVarLines,
      'try {',
      '  const j = pm.response.json();',
      '  if (j.token) setVar("api_token", j.token);',
      '} catch (e) {}',
    ],
    type: 'text/javascript',
  },
};

const saveApiTokenIdTests = {
  listen: 'test',
  script: {
    exec: [
      ...setVarLines,
      'try {',
      '  const j = pm.response.json();',
      '  const items = j.items || (j.data && j.data.items) || [];',
      '  const hit = items.find((r) => r && r.name === "Newman API Token");',
      '  if (hit && hit.id) setVar("api_token_id", hit.id);',
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

function loginAs(name, username) {
  return {
    name,
    event: [loginTests],
    request: {
      method: 'POST',
      header: [jsonH],
      body: {
        mode: 'raw',
        raw: JSON.stringify({ username, password: '123456' }, null, 2),
      },
      url: url('auth/password/login', undefined),
    },
  };
}

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
    { key: 'scoring_scheme_id', value: 'scheme-default-001' },
    { key: 'assessment_rule_id', value: 'arule-default-001' },
    { key: 'employee_id', value: 'demo_emp_01' },
    { key: 'evaluation_period_id', value: '' },
    { key: 'award_id', value: '' },
    { key: 'newman_perf_period', value: '' },
    { key: 'newman_eval_period_key', value: '' },
    { key: 'newman_eval_quarter_key', value: '' },
    { key: 'newman_eval_quarter_id', value: '' },
    { key: 'newman_feishu_subject_code', value: 'default' },
    { key: 'newman_feishu_open_id', value: 'ou_newman_placeholder' },
    { key: 'api_token', value: '' },
    { key: 'api_token_id', value: '' },
  ],
  item: [
    {
      name: '00 Auth',
      item: [
        {
          name: '00 Feishu subjects (public)',
          event: [http2xxTests],
          request: {
            method: 'GET',
            header: [],
            url: url('auth/feishu/subjects'),
          },
        },
        {
          name: '00b Actuator health (public)',
          event: [http2xxTests],
          request: {
            method: 'GET',
            header: [],
            url: url('actuator/health'),
          },
        },
        {
          name: '00c Actuator mappings (public)',
          event: [http2xxTests],
          request: {
            method: 'GET',
            header: [],
            url: url('actuator/mappings'),
          },
        },
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
        {
          name: '02 Feishu exchange (browser flow)',
          event: [],
          request: {
            method: 'GET',
            header: [],
            url: url('auth/feishu/exchange', [
              ['code', '{{feishu_oauth_code}}'],
              ['state', '{{feishu_oauth_state}}'],
            ]),
          },
        },
      ],
    },
    {
      name: '01 Session',
      item: [req('GET session me', 'GET', 'api/session/me')],
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
      name: '03b API Tokens',
      item: [
        req('GET api tokens', 'GET', 'api/admin/api-tokens', { events: [saveApiTokenIdTests] }),
        req('POST api token', 'POST', 'api/admin/api-tokens', {
          json: true,
          body: { name: 'Newman API Token', expiresAt: null },
          events: [saveApiTokenTests],
        }),
        req('GET session me (Bearer)', 'GET', 'api/session/me', {
          events: [http2xxTests],
          noAuth: true,
          bearer: true,
        }),
        req('GET api tokens after create', 'GET', 'api/admin/api-tokens', { events: [saveApiTokenIdTests] }),
        req('DELETE api token', 'DELETE', 'api/admin/api-tokens/{{api_token_id}}'),
      ],
    },
    {
      name: '04 Employees',
      item: [
        req('GET employees', 'GET', 'api/employees', {
          query: [
            ['page', '1'],
            ['pageSize', '20'],
            ['subjectCode', '{{newman_feishu_subject_code}}'],
          ],
        }),
        req('GET employees departments', 'GET', 'api/employees/departments'),
        req('GET employees department-options', 'GET', 'api/employees/department-options', {
          query: [['subjectCode', '{{newman_feishu_subject_code}}']],
        }),
        req('GET employees department-tree', 'GET', 'api/employees/department-tree'),
        req('GET admin departments', 'GET', 'api/admin/departments', {
          query: [
            ['page', '1'],
            ['pageSize', '50'],
          ],
        }),
        req('POST admin departments', 'POST', 'api/admin/departments', {
          json: true,
          body: {
            subjectCode: '{{newman_feishu_subject_code}}',
            name: 'Newman测试部门',
            sortOrder: 0,
          },
        }),
        req('POST admin departments sync', 'POST', 'api/admin/departments/sync-from-employees'),
        req('GET admin auth-config', 'GET', 'api/admin/auth-config'),
        req('PATCH admin auth-config', 'PATCH', 'api/admin/auth-config', {
          json: true,
          body: { passwordLoginEnabled: true },
        }),
        req('GET employees role-options', 'GET', 'api/employees/role-options'),
        req('GET employees feishu-user-options', 'GET', 'api/employees/feishu-user-options', {
          query: [['subjectCode', '{{newman_feishu_subject_code}}']],
        }),
        req('GET employees feishu-user-profile', 'GET', 'api/employees/feishu-user-profile', {
          query: [
            ['subjectCode', '{{newman_feishu_subject_code}}'],
            ['openId', '{{newman_feishu_open_id}}'],
          ],
        }),
        req('GET employees all', 'GET', 'api/employees/all', {
          query: [['subjectCode', '{{newman_feishu_subject_code}}']],
        }),
        req('GET employees calibration-assignees', 'GET', 'api/employees/calibration-assignees'),
        req('PUT employees calibration-assignees', 'PUT', 'api/employees/calibration-assignees', {
          json: true,
          body: { employeeIds: [] },
        }),
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
          body: { clearExisting: false, subjectCode: '{{newman_feishu_subject_code}}' },
        }),
        req('POST employees sync-from-feishu', 'POST', 'api/employees/sync-from-feishu', {
          json: true,
          body: {},
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
        }),
        req('GET performances month-periods', 'GET', 'api/performances/create/month-periods'),
        req('GET performances filter month-periods', 'GET', 'api/performances/filter/month-periods'),
        req('GET performances export', 'GET', 'api/performances/export', {
          query: [['period', '2026-01']],
        }),
        req('GET performances calibration queue', 'GET', 'api/performances/calibration/supervisor-queue', {
          query: [
            ['page', '1'],
            ['pageSize', '10'],
          ],
        }),
        req('POST performances batch create', 'POST', 'api/performances', {
          json: true,
          body: {
            employeeIds: ['demo_emp_01'],
            period: '{{newman_perf_period}}',
            subjectCode: '{{newman_feishu_subject_code}}',
            templateId: '{{template_id}}',
            scoringSchemeId: '{{scoring_scheme_id}}',
          },
          events: [assignNewmanPerfPeriodPrerequest, savePerfIdFromBatchTests],
        }),
        req('GET performances by id', 'GET', 'api/performances/{{performance_id}}'),
        loginAs('Login as demo employee', 'demo_emp_01'),
        req('GET performances list (employee)', 'GET', 'api/performances', {
          query: [
            ['page', '1'],
            ['pageSize', '20'],
          ],
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
        loginAs('Login as demo manager', 'demo_manager'),
        req('POST performances approve-goal', 'POST', 'api/performances/{{performance_id}}/approve-goal', {
          json: true,
          body: { approved: true },
        }),
        loginAs('Login as demo employee', 'demo_emp_01'),
        req('POST performances submit self', 'POST', 'api/performances/{{performance_id}}/submit', {
          json: true,
          body: { reviewType: 'self', content: DEMO_REVIEW, personalSummary: 'newman' },
        }),
        loginAs('Login as demo manager', 'demo_manager'),
        req('POST performances submit manager', 'POST', 'api/performances/{{performance_id}}/submit', {
          json: true,
          body: { reviewType: 'manager', content: DEMO_REVIEW },
        }),
        req('POST performances submit dotted', 'POST', 'api/performances/{{performance_id}}/submit', {
          json: true,
          body: { reviewType: 'dotted_manager', content: DEMO_REVIEW },
          skipAssert: true,
        }),
        req('POST performances reject', 'POST', 'api/performances/{{performance_id}}/reject', {
          json: true,
          body: { reason: 'newman' },
          skipAssert: true,
        }),
        loginAs('Login as super admin', 'zhou_rong'),
        req('POST performances ops reject-self-review', 'POST', 'api/performances/ops/reject-self-review', {
          json: true,
          body: { recordId: '{{performance_id}}', reason: 'newman admin reject' },
          skipAssert: true,
        }),
        req('POST performances calibrate', 'POST', 'api/performances/{{performance_id}}/calibrate', {
          json: true,
          body: { approved: true, finalScore: 4.2 },
        }),
        req('POST performances confirm-result', 'POST', 'api/performances/{{performance_id}}/confirm-result'),
        req('DELETE performances by id', 'DELETE', 'api/performances/{{performance_id}}'),
      ],
    },
    {
      name: '05b Assessment rules',
      item: [
        req('GET assessment-rules list', 'GET', 'api/admin/assessment-rules', {
          query: [
            ['page', '1'],
            ['pageSize', '50'],
          ],
        }),
        req('GET assessment-rules by id', 'GET', 'api/admin/assessment-rules/{{assessment_rule_id}}'),
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
              { name: '指标A', weight: 40, description: 'd' },
              { name: '指标B', weight: 40, description: 'd' },
            ],
          },
        }),
        req('PATCH templates', 'PATCH', 'api/admin/templates/{{template_id}}', {
          json: true,
          body: { name: '通用绩效考核模板' },
        }),
        req('POST templates toggle-status', 'POST', 'api/admin/templates/{{template_id}}/toggle-status'),
        req('POST templates toggle-status restore', 'POST', 'api/admin/templates/{{template_id}}/toggle-status'),
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
            subjectCode: '{{newman_feishu_subject_code}}',
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
        req('GET platform-settings', 'GET', 'api/admin/platform-settings'),
        req('PATCH platform-settings', 'PATCH', 'api/admin/platform-settings', {
          json: true,
          body: { appBadgeEnabled: true, feishuTaskEnabled: true, passwordLoginEnabled: false },
        }),
        req('GET performance-feishu-task-config', 'GET', 'api/admin/performance-feishu-task-config'),
        req('PATCH performance-feishu-task-config', 'PATCH', 'api/admin/performance-feishu-task-config', {
          json: true,
          body: { enabled: true, items: [{ nodeKey: 'goal', dueDays: 7 }] },
        }),
        req('GET feishu app-badge-enabled', 'GET', 'api/feishu/app-badge/enabled'),
        req('GET feishu jssdk-config', 'GET', 'api/feishu/jssdk-config', {
          query: [['url', 'http://127.0.0.1:5174/todo']],
        }),
        req('POST feishu app-badge-sync', 'POST', 'api/feishu/app-badge/sync'),
        req('POST feishu badge-client-log', 'POST', 'api/feishu/badge-client-log', {
          body: { lines: ['[feishu-badge] newman test'] },
        }),
      ],
    },
    {
      name: '09 Evaluation periods',
      item: [
        req('GET evaluation-periods', 'GET', 'api/admin/evaluation-periods', {
          query: [['period_type', 'month']],
        }),
        req('POST evaluation-periods (quarter prereq)', 'POST', 'api/admin/evaluation-periods', {
          json: true,
          body: {
            periodType: 'quarter',
            periodKey: '{{newman_eval_quarter_key}}',
            name: 'Newman季度',
            sortOrder: 998,
            status: 'draft',
          },
          events: [assignNewmanEvalPeriodPrerequest, saveNewmanEvalQuarterIdFromCreateTests],
        }),
        req('POST evaluation-periods', 'POST', 'api/admin/evaluation-periods', {
          json: true,
          body: {
            periodType: 'month',
            periodKey: '{{newman_eval_period_key}}',
            name: 'Newman月度',
            sortOrder: 999,
            status: 'draft',
            parentPeriodId: '{{newman_eval_quarter_id}}',
          },
          events: [saveEvalPeriodIdFromCreateTests],
        }),
        req('PUT evaluation-periods', 'PUT', 'api/admin/evaluation-periods/{{evaluation_period_id}}', {
          json: true,
          body: { name: 'Newman月度改名' },
        }),
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
        req('GET evaluation leaderboard quarter detail', 'GET', 'api/admin/evaluation/leaderboard/quarter-detail', {
          query: [
            ['key', '2026-Q2'],
            ['employeeId', '{{employee_id}}'],
          ],
        }),
        req('GET evaluation awards', 'GET', 'api/admin/evaluation/awards', {
          query: [['periodId', '{{evaluation_period_id}}']],
        }),
        req('POST evaluation awards', 'POST', 'api/admin/evaluation/awards', {
          json: true,
          body: {
            periodId: '{{evaluation_period_id}}',
            awardCode: 'monthly_excellence',
            employeeId: '{{employee_id}}',
            remark: 'newman',
          },
          events: [saveAwardIdTests],
        }),
        req('DELETE evaluation award', 'DELETE', 'api/admin/evaluation/awards/{{award_id}}'),
        req('DELETE evaluation-periods', 'DELETE', 'api/admin/evaluation-periods/{{evaluation_period_id}}'),
        req('DELETE evaluation-periods quarter', 'DELETE', 'api/admin/evaluation-periods/{{newman_eval_quarter_id}}'),
      ],
    },
    {
      name: '99 Teardown',
      item: [req('POST session logout', 'POST', 'api/session/logout', { json: true, body: {} })],
    },
  ],
};

const outPath = join(__dirname, 'jixiao2-api.postman_collection.json');
fs.writeFileSync(outPath, JSON.stringify(collection, null, 2), 'utf8');
console.error('Wrote', outPath);

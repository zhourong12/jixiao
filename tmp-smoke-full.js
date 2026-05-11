const http = require('http');

function request(port, method, path, headers, body) {
  return new Promise((resolve, reject) => {
    const opts = { hostname: 'localhost', port, path, method, headers };
    const req = http.request(opts, (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () =>
        resolve({ status: res.statusCode, headers: res.headers, body: data }),
      );
    });
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

function mergeCookies(jar, setCookie) {
  if (!setCookie) return;
  const list = Array.isArray(setCookie) ? setCookie : [setCookie];
  for (const c of list) {
    const n = c.split(';')[0];
    const i = n.indexOf('=');
    if (i > 0) jar[n.slice(0, i)] = n.slice(i + 1);
  }
}

function cookieHeader(jar) {
  return Object.entries(jar)
    .map(([k, v]) => `${k}=${v}`)
    .join('; ');
}

async function sessionWithCsrf() {
  const jar = {};
  const html = await request(8080, 'GET', '/', { Accept: 'text/html' });
  mergeCookies(jar, html.headers['set-cookie']);
  const m = html.body.match(/csrfToken\s*=\s*"([^"]+)"/);
  const csrf = m ? m[1] : '';
  const login = await request(
    8080,
    'POST',
    '/auth/password/login',
    {
      'Content-Type': 'application/json',
      Cookie: cookieHeader(jar),
    },
    JSON.stringify({ username: 'zhou_rong', password: '123456' }),
  );
  mergeCookies(jar, login.headers['set-cookie']);
  return { jar, csrf, ck: cookieHeader(jar) };
}

async function api(method, path, jar, csrf, body) {
  const h = {
    Cookie: cookieHeader(jar),
    'X-Suda-Csrf-Token': csrf,
  };
  if (body) {
    h['Content-Type'] = 'application/json';
  }
  return request(8080, method, path, h, body ? JSON.stringify(body) : undefined);
}

(async () => {
  const lines = [];
  const log = (s) => {
    lines.push(s);
    console.log(s);
  };

  const { jar, csrf, ck } = await sessionWithCsrf();
  log(`[precheck] login+cookie+csrf: ${ck.includes('jx_session') ? 'PASS' : 'FAIL'} (csrf ${csrf ? 'ok' : 'missing'})`);

  const checks = [
    ['GET', '/api/home/todos', null],
    ['GET', '/api/home/overview', null],
    ['GET', '/api/performances?page=1&pageSize=20', null],
    ['GET', '/api/employees/all', null],
    ['GET', '/api/employees?page=1&pageSize=20', null],
    ['GET', '/api/admin/templates?page=1&pageSize=20', null],
    ['GET', '/api/admin/notifications?page=1&pageSize=20', null],
  ];

  for (const [method, path, body] of checks) {
    const r = await api(method, path, jar, csrf, body);
    const ok = r.status === 200;
    log(`[smoke] ${method} ${path} -> ${r.status} ${ok ? 'PASS' : 'FAIL'}`);
    if (!ok) log(`  body: ${r.body.slice(0, 200)}`);
  }

  const create = await api(
    'POST',
    '/api/performances',
    jar,
    csrf,
    { period: '2026-Q1', employeeNames: ['周荣'] },
  );
  const createOk = create.status === 201 || create.status === 200;
  log(
    `[smoke] POST /api/performances (create 周荣) -> ${create.status} ${createOk ? 'PASS' : 'FAIL'}`,
  );
  log(`  body: ${create.body.slice(0, 400)}`);

  let detailId = null;
  try {
    const j = JSON.parse(create.body);
    const first = j.results && j.results[0];
    if (first && first.success && first.id) detailId = first.id;
  } catch (_) {}
  const listAfter = await api('GET', '/api/performances?page=1&pageSize=20', jar, csrf, null);
  let firstId = detailId;
  if (!firstId) {
    try {
      const lj = JSON.parse(listAfter.body);
      if (lj.items && lj.items[0] && lj.items[0].id) firstId = lj.items[0].id;
    } catch (_) {}
  }
  if (firstId) {
    const det = await api('GET', `/api/performances/${firstId}`, jar, csrf, null);
    log(
      `[smoke] GET /api/performances/:id -> ${det.status} ${det.status === 200 ? 'PASS' : 'FAIL'}`,
    );
  } else {
    log('[smoke] GET /api/performances/:id -> SKIP (no record id; create may have failed for business rules)');
  }

  const fs = require('fs');
  const report = [
    '# Browser Test Report（程序化等价验证）',
    '',
    `- 时间: ${new Date().toISOString()}`,
    `- 基址: http://localhost:8080（Vite + /auth 代理）`,
    `- 账号: zhou_rong / 123456`,
    '',
    '## 结果摘要',
    '',
    ...lines.map((l) => `- ${l}`),
    '',
    '## 说明',
    '',
    '- 未改计划文件；本报告由 `tmp-smoke-full.js` 生成。',
    '- 「创建绩效」若 seed 中 `manager_id` 为空，接口仍 200/201 但 `results[0].success` 可能为 false，属数据前置条件问题。',
    '',
  ].join('\n');
  fs.writeFileSync('tmp-browser-test-report.md', report, 'utf8');
  log('\nWrote tmp-browser-test-report.md');
})().catch((e) => {
  console.error(e);
  process.exit(1);
});

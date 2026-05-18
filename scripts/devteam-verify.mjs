/**
 * dev-team 阶段 A/B API 回归（见 test/devteam-regression-checklist.md）
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const baseUrl = process.env.DEVTEAM_API_BASE || process.env.BASE_URL || 'http://localhost:8081';
const password = process.env.DEVTEAM_PASSWORD || '123456';
const templateId = 'tpl-demo-001';
const assessmentRuleId = 'arule-default-001';
const subjectCode = 'default';

const goals = [
  { indicatorName: '业绩目标', target: 'T1', weight: 40 },
  { indicatorName: '团队协作', target: 'T2', weight: 30 },
  { indicatorName: '专业能力', target: 'T3', weight: 30 },
];

const review = [
  { indicatorName: '业绩目标', score: 4, comment: 'ok' },
  { indicatorName: '团队协作', score: 4, comment: 'ok' },
  { indicatorName: '专业能力', score: 4, comment: 'ok' },
];

const failures = [];

function fail(label, detail) {
  failures.push({ label, detail });
  console.error(`FAIL ${label}: ${detail}`);
}

function ok(label) {
  console.log(`OK ${label}`);
}

/** 飞书 OAuth 公开入口：无 Cookie 也可访问 */
async function verifyPublicFeishuRoutes() {
  const subjects = await fetch(`${baseUrl}/auth/feishu/subjects`);
  if (subjects.status !== 200) {
    fail('GET /auth/feishu/subjects', `HTTP ${subjects.status}`);
  } else {
    const j = await subjects.json().catch(() => null);
    if (!j || !Array.isArray(j.items)) {
      fail('GET /auth/feishu/subjects', '响应缺少 items 数组');
    } else ok('GET /auth/feishu/subjects 200 + items');
  }

  const noSubject = await fetch(`${baseUrl}/auth/feishu/login`, { redirect: 'manual' });
  if (noSubject.status !== 302 && noSubject.status !== 301) {
    fail('GET /auth/feishu/login 无 subjectCode', `期望 302，实际 HTTP ${noSubject.status}`);
    return;
  }
  const loc = noSubject.headers.get('location') || '';
  if (!loc.includes('/login')) {
    fail('GET /auth/feishu/login 无 subjectCode 重定向', loc);
  } else ok('GET /auth/feishu/login 无 subjectCode → /login');
}

function sessionCookie(response) {
  const raw =
    response.headers.getSetCookie?.() ||
    (response.headers.get('set-cookie') ? [response.headers.get('set-cookie')] : []);
  const hit = raw.find((line) => String(line).startsWith('jx_session='));
  return hit ? hit.split(';')[0] : '';
}

async function login(username) {
  const response = await fetch(`${baseUrl}/auth/password/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!response.ok) {
    throw new Error(`登录 ${username} 失败: HTTP ${response.status}`);
  }
  const cookie = sessionCookie(response);
  if (!cookie) {
    throw new Error(`登录 ${username} 未返回 jx_session`);
  }
  return cookie;
}

async function api(cookie, method, pathname, body) {
  const response = await fetch(`${baseUrl}${pathname}`, {
    method,
    headers: {
      Cookie: cookie,
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let json = null;
  try {
    json = await response.json();
  } catch {
    json = null;
  }
  return { status: response.status, json };
}

async function ensurePersona(adminCookie, spec) {
  const all = await api(adminCookie, 'GET', '/api/employees/all');
  const items = all.json?.items || [];
  const exists = items.some((row) => row.userId === spec.userId || row.employeeId === spec.userId);
  if (!exists) {
    const created = await api(adminCookie, 'POST', '/api/employees', {
      userId: spec.userId,
      name: spec.name,
      departmentName: spec.departmentName || '管理部',
      managerId: spec.managerId ?? null,
      dottedManagerId: spec.dottedManagerId ?? null,
      roleKey: spec.roleKey,
    });
    if (created.status !== 200) {
      throw new Error(`创建 ${spec.userId} 失败: ${created.status} ${JSON.stringify(created.json)}`);
    }
    return;
  }
  const body = {
    name: spec.name,
    departmentName: spec.departmentName || '管理部',
  };
  if (spec.managerId !== undefined) body.managerId = spec.managerId;
  if (spec.dottedManagerId !== undefined) body.dottedManagerId = spec.dottedManagerId;
  if (spec.roleKey !== undefined) body.roleKey = spec.roleKey;
  const updated = await api(adminCookie, 'PUT', `/api/employees/${spec.userId}`, body);
  if (updated.status !== 200) {
    throw new Error(`更新 ${spec.userId} 失败: ${updated.status} ${JSON.stringify(updated.json)}`);
  }
}

async function ensureDemoTemplateEnabled(adminCookie) {
  const detail = await api(adminCookie, 'GET', '/api/admin/templates/tpl-demo-001');
  if (detail.json?.status === 'disabled') {
    const toggled = await api(adminCookie, 'POST', '/api/admin/templates/tpl-demo-001/toggle-status');
    if (toggled.status !== 200) {
      throw new Error(`启用模板失败: ${toggled.status}`);
    }
  }
}

async function bootstrapPersonas() {
  const admin = await login('zhou_rong');
  await ensureDemoTemplateEnabled(admin);
  await ensurePersona(admin, {
    userId: 'demo_dotted_mgr',
    name: '演示虚线上级',
    managerId: null,
  });
  await ensurePersona(admin, {
    userId: 'qa_admin',
    name: 'QA管理员',
    managerId: 'demo_manager',
    roleKey: 'admin',
  });
  await ensurePersona(admin, {
    userId: 'qa_emp_dotted',
    name: '虚线链路员工',
    managerId: 'demo_manager',
    dottedManagerId: 'demo_dotted_mgr',
  });
  await ensurePersona(admin, {
    userId: 'demo_emp_01',
    name: '张三',
    managerId: 'demo_manager',
    roleKey: 'employee',
  });
  await ensurePersona(admin, {
    userId: 'demo_emp_02',
    name: '李四',
    managerId: 'demo_manager',
    roleKey: 'employee',
  });
  ok('测试身份已就绪');
}

async function createPerformance(adminCookie, employeeName, period) {
  const created = await api(adminCookie, 'POST', '/api/performances', {
    employeeNames: [employeeName],
    period,
    templateId,
    assessmentRuleId,
    subjectCode,
  });
  const row = created.json?.results?.find((item) => item.success && item.id);
  if (!row) {
    throw new Error(`创建 ${employeeName} 绩效失败: ${JSON.stringify(created.json)}`);
  }
  return row.id;
}

async function runLinearFlow(period) {
  const admin = await login('zhou_rong');
  const perfId = await createPerformance(admin, '张三', period);

  const emp = await login('demo_emp_01');
  let res = await api(emp, 'POST', `/api/performances/${perfId}/select-template`, { templateId });
  if (res.status !== 200) throw new Error(`select-template ${res.status}`);
  res = await api(emp, 'POST', `/api/performances/${perfId}/submit`, { reviewType: 'goal', content: goals });
  if (res.status !== 200 || res.json?.newStatus !== 'goal_pending_review') {
    throw new Error(`submit goal ${res.status} ${JSON.stringify(res.json)}`);
  }

  const manager = await login('demo_manager');
  res = await api(manager, 'POST', `/api/performances/${perfId}/approve-goal`, { approved: true });
  if (res.status !== 200 || res.json?.newStatus !== 'self_review') {
    throw new Error(`approve goal ${res.status} ${JSON.stringify(res.json)}`);
  }

  res = await api(emp, 'POST', `/api/performances/${perfId}/submit`, {
    reviewType: 'self',
    content: review,
    personalSummary: 'devteam',
  });
  if (res.status !== 200 || res.json?.newStatus !== 'manager_review') {
    throw new Error(`submit self ${res.status} ${JSON.stringify(res.json)}`);
  }

  res = await api(manager, 'POST', `/api/performances/${perfId}/submit`, {
    reviewType: 'manager',
    content: review,
  });
  if (res.status !== 200 || res.json?.newStatus !== 'final_review') {
    throw new Error(`submit manager ${res.status} ${JSON.stringify(res.json)}`);
  }

  res = await api(admin, 'POST', `/api/performances/${perfId}/calibrate`, {
    approved: true,
    finalScore: 4.2,
  });
  if (res.status !== 200) throw new Error(`calibrate ${res.status}`);

  res = await api(emp, 'GET', `/api/performances/${perfId}`);
  if (res.status !== 200 || res.json?.status !== 'completed') {
    throw new Error(`detail completed ${res.status} ${res.json?.status}`);
  }
}

async function runDottedFlow(period) {
  const admin = await login('zhou_rong');
  const perfId = await createPerformance(admin, '虚线链路员工', period);
  const emp = await login('qa_emp_dotted');
  const manager = await login('demo_manager');
  const dotted = await login('demo_dotted_mgr');

  let res = await api(emp, 'POST', `/api/performances/${perfId}/select-template`, { templateId });
  if (res.status !== 200) throw new Error(`dotted select-template ${res.status}`);
  res = await api(emp, 'POST', `/api/performances/${perfId}/submit`, { reviewType: 'goal', content: goals });
  if (res.status !== 200) throw new Error(`dotted submit goal ${res.status}`);
  res = await api(manager, 'POST', `/api/performances/${perfId}/approve-goal`, { approved: true });
  if (res.status !== 200) throw new Error(`dotted approve goal ${res.status}`);
  res = await api(emp, 'POST', `/api/performances/${perfId}/submit`, {
    reviewType: 'self',
    content: review,
    personalSummary: 'devteam',
  });
  if (res.status !== 200 || res.json?.newStatus !== 'dual_manager_review') {
    throw new Error(`dotted submit self ${res.status} ${JSON.stringify(res.json)}`);
  }
  res = await api(manager, 'POST', `/api/performances/${perfId}/submit`, {
    reviewType: 'manager',
    content: review,
  });
  if (res.status !== 200 || res.json?.newStatus !== 'dual_manager_review') {
    throw new Error(`dotted manager submit ${res.status} ${JSON.stringify(res.json)}`);
  }
  res = await api(dotted, 'POST', `/api/performances/${perfId}/submit`, {
    reviewType: 'dotted_manager',
    content: review,
  });
  if (res.status !== 200 || res.json?.newStatus !== 'final_review') {
    throw new Error(`dotted dotted submit ${res.status} ${JSON.stringify(res.json)}`);
  }
}

async function runGoalRejectFlow(period) {
  const admin = await login('zhou_rong');
  const perfId = await createPerformance(admin, '李四', period);
  const emp = await login('demo_emp_02');
  const manager = await login('demo_manager');

  let res = await api(emp, 'POST', `/api/performances/${perfId}/select-template`, { templateId });
  if (res.status !== 200) throw new Error(`reject select-template ${res.status}`);
  res = await api(emp, 'POST', `/api/performances/${perfId}/submit`, { reviewType: 'goal', content: goals });
  if (res.status !== 200) throw new Error(`reject submit goal ${res.status}`);
  res = await api(manager, 'POST', `/api/performances/${perfId}/approve-goal`, {
    approved: false,
    rejectionReason: 'devteam',
  });
  if (res.status !== 200 || res.json?.newStatus !== 'goal_rejected') {
    throw new Error(`reject approve goal ${res.status} ${JSON.stringify(res.json)}`);
  }
  res = await api(emp, 'POST', `/api/performances/${perfId}/submit`, { reviewType: 'goal', content: goals });
  if (res.status !== 200 || res.json?.newStatus !== 'goal_pending_review') {
    throw new Error(`reject resubmit goal ${res.status} ${JSON.stringify(res.json)}`);
  }
}

async function phaseA() {
  const stamp = Date.now();
  const periodA1 = `2099-dt-a1-${stamp}`;
  const periodA2 = `2099-dt-a2-${stamp}`;
  const admin = await login('zhou_rong');
  const emp1 = await login('demo_emp_01');
  const emp2 = await login('demo_emp_02');
  const qaAdmin = await login('qa_admin');

  const idA1 = await createPerformance(admin, '张三', periodA1);
  const idA2 = await createPerformance(admin, '李四', periodA2);

  const list1 = await api(emp1, 'GET', '/api/performances?page=1&pageSize=50');
  const list2 = await api(emp2, 'GET', '/api/performances?page=1&pageSize=50');
  const ids1 = new Set((list1.json?.items || []).map((row) => row.id));
  const ids2 = new Set((list2.json?.items || []).map((row) => row.id));
  if (ids2.has(idA1)) fail('A2 列表不含 A1', `demo_emp_02 可见 ${idA1}`);
  else ok('A2 列表不含 A1');
  if (ids1.has(idA2)) fail('A1 列表不含 A2', `demo_emp_01 可见 ${idA2}`);
  else ok('A1 列表不含 A2');

  const superList = await api(admin, 'GET', '/api/performances?page=1&pageSize=5');
  if (!superList.json?.canExport || !superList.json?.canBatchCreate) {
    fail('超管列表权限', JSON.stringify(superList.json));
  } else ok('超管 canExport / canBatchCreate');

  const qaList = await api(qaAdmin, 'GET', '/api/performances?page=1&pageSize=5');
  if (qaList.json?.canExport || qaList.json?.canBatchCreate) {
    fail('qa_admin 列表权限', JSON.stringify(qaList.json));
  } else ok('qa_admin 无导出/批量创建');

  const foreign = await api(emp1, 'GET', `/api/performances/${idA2}`);
  if (foreign.status !== 403) fail('无关用户详情 403', `status=${foreign.status}`);
  else ok('无关用户详情 403');

  const qaCreate = await api(qaAdmin, 'POST', '/api/performances', {
    employeeNames: ['张三'],
    period: `2099-dt-qa-${stamp}`,
    templateId,
    assessmentRuleId,
    subjectCode,
  });
  if (qaCreate.status !== 403) fail('qa_admin 批量创建 403', `status=${qaCreate.status}`);
  else ok('qa_admin 批量创建 403');

  const qaExport = await api(qaAdmin, 'GET', '/api/performances/export?period=2026-01');
  if (qaExport.status !== 403) fail('qa_admin 导出 403', `status=${qaExport.status}`);
  else ok('qa_admin 导出 403');

  const qaQueue = await api(
    qaAdmin,
    'GET',
    '/api/performances/calibration/supervisor-queue?page=1&pageSize=5',
  );
  if (qaQueue.status !== 403) fail('qa_admin 校准队列 403', `status=${qaQueue.status}`);
  else ok('qa_admin 校准队列 403');

  const exportOk = await api(admin, 'GET', '/api/performances/export?period=2026-01');
  if (exportOk.status !== 200) fail('超管导出 200', `status=${exportOk.status}`);
  else ok('超管导出 200');
}

async function phaseB() {
  const stamp = Date.now();
  try {
    await runLinearFlow(`2099-dt-b-linear-${stamp}`);
    ok('B 无虚线至 completed');
  } catch (error) {
    fail('B 无虚线至 completed', error.message);
  }
  try {
    await runDottedFlow(`2099-dt-b-dotted-${stamp}`);
    ok('B 虚线 dual_manager_review → final_review');
  } catch (error) {
    fail('B 虚线链路', error.message);
  }
  try {
    await runGoalRejectFlow(`2099-dt-b-reject-${stamp}`);
    ok('B 目标驳回后再提交');
  } catch (error) {
    fail('B 目标驳回后再提交', error.message);
  }
}

async function main() {
  console.log(`dev-team API 验证: ${baseUrl}`);
  try {
    await verifyPublicFeishuRoutes();
    await bootstrapPersonas();
    await phaseA();
    await phaseB();
  } catch (error) {
    fail('环境', error.message);
  }

  if (failures.length) {
    console.error(`\n${failures.length} 项失败`);
    process.exit(1);
  }
  console.log('\n阶段 A/B 全部通过');
}

main();

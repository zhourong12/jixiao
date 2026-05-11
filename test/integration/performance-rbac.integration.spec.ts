import request from 'supertest';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { signSessionToken } from '../../server/utils/session-token';
import { createIntegrationApp } from './nest-integration-app';

const run = process.env.JIXIAO_INTEGRATION === '1';

function sessionCookie(userId: string, displayName: string): string {
  const t = signSessionToken(userId, displayName, []);
  if (!t) throw new Error('signSessionToken failed');
  return `jx_session=${encodeURIComponent(t)}`;
}

(run ? describe : describe.skip)('Performance RBAC & isolation (JIXIAO_INTEGRATION=1)', () => {
  let app: NestExpressApplication;
  let createdPerfId: string;

  beforeAll(async () => {
    app = await createIntegrationApp();
    const res = await request(app.getHttpServer())
      .post('/api/performances')
      .set('Cookie', sessionCookie('zhou_rong', '周荣'))
      .send({
        employeeNames: ['张三'],
        period: `devteam-rbac-${Date.now()}`,
      })
      .expect(200);
    const body = res.body as { results?: Array<{ success?: boolean; id?: string }> };
    const row = body.results?.find((r) => r.success && r.id);
    if (!row?.id) {
      throw new Error('Integration setup: batch create 张三 failed');
    }
    createdPerfId = row.id;
  }, 120000);

  afterAll(async () => {
    await app?.close();
  });

  it('GET /api/performances as 李四 does not include 张三新建记录', async () => {
    const res = await request(app.getHttpServer())
      .get('/api/performances?page=1&pageSize=100')
      .set('Cookie', sessionCookie('demo_emp_02', '李四'))
      .expect(200);
    const ids = (res.body.items as { id: string }[]).map((i) => i.id);
    expect(ids).not.toContain(createdPerfId);
  });

  it('GET /api/performances/:id as 李四 returns 403', async () => {
    await request(app.getHttpServer())
      .get(`/api/performances/${createdPerfId}`)
      .set('Cookie', sessionCookie('demo_emp_02', '李四'))
      .expect(403);
  });

  it('GET /api/performances/:id as 张三 returns 200', async () => {
    await request(app.getHttpServer())
      .get(`/api/performances/${createdPerfId}`)
      .set('Cookie', sessionCookie('demo_emp_01', '张三'))
      .expect(200);
  });

  it('qa_admin cannot export', async () => {
    await request(app.getHttpServer())
      .get('/api/performances/export')
      .set('Cookie', sessionCookie('qa_admin', 'QA管理员'))
      .expect(403);
  });

  it('qa_admin cannot batch create', async () => {
    await request(app.getHttpServer())
      .post('/api/performances')
      .set('Cookie', sessionCookie('qa_admin', 'QA管理员'))
      .send({ employeeNames: ['张三'], period: 'devteam-should-fail' })
      .expect(403);
  });

  it('qa_admin cannot calibration queue', async () => {
    await request(app.getHttpServer())
      .get('/api/performances/calibration/supervisor-queue?page=1&pageSize=10')
      .set('Cookie', sessionCookie('qa_admin', 'QA管理员'))
      .expect(403);
  });

  it('zhou_rong list includes canExport and canBatchCreate', async () => {
    const res = await request(app.getHttpServer())
      .get('/api/performances?page=1&pageSize=5')
      .set('Cookie', sessionCookie('zhou_rong', '周荣'))
      .expect(200);
    expect(res.body.canExport).toBe(true);
    expect(res.body.canBatchCreate).toBe(true);
  });

  it('qa_admin list has canExport and canBatchCreate false', async () => {
    const res = await request(app.getHttpServer())
      .get('/api/performances?page=1&pageSize=5')
      .set('Cookie', sessionCookie('qa_admin', 'QA管理员'))
      .expect(200);
    expect(res.body.canExport).toBe(false);
    expect(res.body.canBatchCreate).toBe(false);
  });

  it('zhou_rong export returns 200', async () => {
    const res = await request(app.getHttpServer())
      .get('/api/performances/export')
      .set('Cookie', sessionCookie('zhou_rong', '周荣'))
      .expect(200);
    expect(Array.isArray(res.body.items)).toBe(true);
  });
});

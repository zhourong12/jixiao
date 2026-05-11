import request from 'supertest';
import type { NestExpressApplication } from '@nestjs/platform-express';
import type { GoalSettingItem, ReviewItem } from '../../shared/api.interface';
import { signSessionToken } from '../../server/utils/session-token';
import { createIntegrationApp } from './nest-integration-app';

const run = process.env.JIXIAO_INTEGRATION === '1';

const INDICATORS = ['业绩目标', '团队协作', '专业能力'] as const;

function sessionCookie(userId: string, displayName: string): string {
  const t = signSessionToken(userId, displayName, []);
  if (!t) throw new Error('signSessionToken failed');
  return `jx_session=${encodeURIComponent(t)}`;
}

function fullGoals(): GoalSettingItem[] {
  return INDICATORS.map((indicatorName) => ({
    indicatorName,
    target: 'integration-target',
    weight: 33,
  }));
}

function fullReview(): ReviewItem[] {
  return INDICATORS.map((indicatorName) => ({
    indicatorName,
    score: 4,
    comment: 'integration',
  }));
}

function expect2xx(status: number) {
  expect(status).toBeGreaterThanOrEqual(200);
  expect(status).toBeLessThan(300);
}

(run ? describe : describe.skip)('Performance FSM (JIXIAO_INTEGRATION=1)', () => {
  let app: NestExpressApplication;

  beforeAll(async () => {
    app = await createIntegrationApp();
  }, 120000);

  afterAll(async () => {
    await app?.close();
  });

  async function createForEmployee(name: string): Promise<string> {
    const period = `devteam-fsm-${name}-${Date.now()}`;
    const res = await request(app.getHttpServer())
      .post('/api/performances')
      .set('Cookie', sessionCookie('zhou_rong', '周荣'))
      .send({ employeeNames: [name], period })
      .expect(200);
    const body = res.body as { results?: Array<{ success?: boolean; id?: string }> };
    const row = body.results?.find((r) => r.success && r.id);
    if (!row?.id) throw new Error(`batch create failed for ${name}`);
    return row.id;
  }

  async function expectStatus(id: string, status: string) {
    const res = await request(app.getHttpServer())
      .get(`/api/performances/${id}`)
      .set('Cookie', sessionCookie('zhou_rong', '周荣'))
      .expect(200);
    expect((res.body as { status: string }).status).toBe(status);
  }

  it(
    'path1: no dotted line — template → goal → approve → self → manager → final → completed',
    async () => {
      const id = await createForEmployee('张三');

      const sel = await request(app.getHttpServer())
        .post(`/api/performances/${id}/select-template`)
        .set('Cookie', sessionCookie('demo_emp_01', '张三'))
        .send({ templateId: 'tpl-demo-001' });
      expect2xx(sel.status);
      await expectStatus(id, 'goal_setting');

      const g1 = await request(app.getHttpServer())
        .post(`/api/performances/${id}/submit`)
        .set('Cookie', sessionCookie('demo_emp_01', '张三'))
        .send({ reviewType: 'goal', content: fullGoals() });
      expect2xx(g1.status);
      await expectStatus(id, 'goal_pending_review');

      const ag1 = await request(app.getHttpServer())
        .post(`/api/performances/${id}/approve-goal`)
        .set('Cookie', sessionCookie('demo_manager', '演示经理'))
        .send({ approved: true });
      expect2xx(ag1.status);
      await expectStatus(id, 'self_review');

      const self = await request(app.getHttpServer())
        .post(`/api/performances/${id}/submit`)
        .set('Cookie', sessionCookie('demo_emp_01', '张三'))
        .send({ reviewType: 'self', content: fullReview(), personalSummary: 'ok' });
      expect2xx(self.status);
      await expectStatus(id, 'manager_review');

      const mgrRes = await request(app.getHttpServer())
        .post(`/api/performances/${id}/submit`)
        .set('Cookie', sessionCookie('demo_manager', '演示经理'))
        .send({ reviewType: 'manager', content: fullReview() });
      expect2xx(mgrRes.status);
      expect((mgrRes.body as { newStatus: string }).newStatus).toBe('final_review');
      await expectStatus(id, 'final_review');

      const fin = await request(app.getHttpServer())
        .post(`/api/performances/${id}/final-review`)
        .set('Cookie', sessionCookie('zhou_rong', '周荣'))
        .send({ approved: true });
      expect2xx(fin.status);
      expect((fin.body as { newStatus: string }).newStatus).toBe('completed');
      await expectStatus(id, 'completed');
    },
    180000,
  );

  it(
    'path2: dotted — self → dual_manager_review → both managers → final_review',
    async () => {
      const id = await createForEmployee('虚线链路员工');

      const st = await request(app.getHttpServer())
        .post(`/api/performances/${id}/select-template`)
        .set('Cookie', sessionCookie('qa_emp_dotted', '虚线链路员工'))
        .send({ templateId: 'tpl-demo-001' });
      expect2xx(st.status);

      const g2 = await request(app.getHttpServer())
        .post(`/api/performances/${id}/submit`)
        .set('Cookie', sessionCookie('qa_emp_dotted', '虚线链路员工'))
        .send({ reviewType: 'goal', content: fullGoals() });
      expect2xx(g2.status);

      const ag2 = await request(app.getHttpServer())
        .post(`/api/performances/${id}/approve-goal`)
        .set('Cookie', sessionCookie('demo_manager', '演示经理'))
        .send({ approved: true });
      expect2xx(ag2.status);

      const selfRes = await request(app.getHttpServer())
        .post(`/api/performances/${id}/submit`)
        .set('Cookie', sessionCookie('qa_emp_dotted', '虚线链路员工'))
        .send({ reviewType: 'self', content: fullReview(), personalSummary: 'dotted' });
      expect2xx(selfRes.status);
      expect((selfRes.body as { newStatus: string }).newStatus).toBe('dual_manager_review');
      await expectStatus(id, 'dual_manager_review');

      const m2 = await request(app.getHttpServer())
        .post(`/api/performances/${id}/submit`)
        .set('Cookie', sessionCookie('demo_manager', '演示经理'))
        .send({ reviewType: 'manager', content: fullReview() });
      expect2xx(m2.status);
      await expectStatus(id, 'dual_manager_review');

      const dotRes = await request(app.getHttpServer())
        .post(`/api/performances/${id}/submit`)
        .set('Cookie', sessionCookie('demo_dotted_mgr', '演示虚线上级'))
        .send({ reviewType: 'dotted_manager', content: fullReview() });
      expect2xx(dotRes.status);
      expect((dotRes.body as { newStatus: string }).newStatus).toBe('final_review');
      await expectStatus(id, 'final_review');
    },
    180000,
  );

  it('path3: goal rejected then resubmit to goal_pending_review', async () => {
    const id = await createForEmployee('李四');

    const st3 = await request(app.getHttpServer())
      .post(`/api/performances/${id}/select-template`)
      .set('Cookie', sessionCookie('demo_emp_02', '李四'))
      .send({ templateId: 'tpl-demo-001' });
    expect2xx(st3.status);

    const g3 = await request(app.getHttpServer())
      .post(`/api/performances/${id}/submit`)
      .set('Cookie', sessionCookie('demo_emp_02', '李四'))
      .send({ reviewType: 'goal', content: fullGoals() });
    expect2xx(g3.status);

    const rj = await request(app.getHttpServer())
      .post(`/api/performances/${id}/approve-goal`)
      .set('Cookie', sessionCookie('demo_manager', '演示经理'))
      .send({ approved: false, rejectionReason: 'integration-reject' });
    expect2xx(rj.status);
    await expectStatus(id, 'goal_rejected');

    const g4 = await request(app.getHttpServer())
      .post(`/api/performances/${id}/submit`)
      .set('Cookie', sessionCookie('demo_emp_02', '李四'))
      .send({ reviewType: 'goal', content: fullGoals() });
    expect2xx(g4.status);
    await expectStatus(id, 'goal_pending_review');
  }, 120000);
});

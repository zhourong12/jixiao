import { existsSync } from 'fs';
import { join } from 'path';
import request from 'supertest';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { signSessionToken } from '../../server/utils/session-token';
import { createIntegrationApp } from './nest-integration-app';

const run = process.env.JIXIAO_INTEGRATION === '1';
const hasClientIndex = existsSync(join(process.cwd(), 'dist/client/index.html'));

function sessionCookie(userId: string, displayName: string): string {
  const t = signSessionToken(userId, displayName, []);
  if (!t) throw new Error('signSessionToken failed');
  return `jx_session=${encodeURIComponent(t)}`;
}

(run && hasClientIndex ? describe : describe.skip)(
  'App shell HTML (JIXIAO_INTEGRATION=1 + dist/client/index.html)',
  () => {
    let app: NestExpressApplication;

    beforeAll(async () => {
      app = await createIntegrationApp();
    }, 120000);

    afterAll(async () => {
      await app?.close();
    });

    const spaPaths = ['/', '/todo', '/performances', '/my-performance', '/admin/templates'];

    it.each(spaPaths)('GET %s returns HTML with platform bootstrap', async (path) => {
      const res = await request(app.getHttpServer())
        .get(path)
        .set('Cookie', sessionCookie('zhou_rong', '周荣'))
        .expect(200);
      expect(res.text).toContain('__platform__');
      expect(res.text).toMatch(/html/i);
    });
  },
);

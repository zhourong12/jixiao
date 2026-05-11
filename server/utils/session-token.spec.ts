import { signSessionToken, verifySessionToken } from './session-token';

describe('session-token', () => {
  const prev = process.env.SESSION_JWT_SECRET;
  beforeAll(() => {
    process.env.SESSION_JWT_SECRET = 'test-session-secret-jixiao2';
  });
  afterAll(() => {
    process.env.SESSION_JWT_SECRET = prev;
  });

  it('signs and verifies round-trip', () => {
    const t = signSessionToken('demo_emp_01', '张三', ['employee']);
    expect(t).toBeTruthy();
    const p = verifySessionToken(t!);
    expect(p?.sub).toBe('demo_emp_01');
    expect(p?.name).toBe('张三');
    expect(p?.roles).toEqual(['employee']);
  });

  it('rejects tampered token', () => {
    const t = signSessionToken('a', 'A', [])!;
    const bad = t.slice(0, -4) + 'xxxx';
    expect(verifySessionToken(bad)).toBeNull();
  });
});

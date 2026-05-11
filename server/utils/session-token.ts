import { createHmac, timingSafeEqual } from 'node:crypto';

export type SessionPayload = {
  sub: string;
  name: string;
  roles: string[];
  feishuOpenId?: string;
  exp: number;
};

const TTL_SEC = 7 * 24 * 3600;

function getSecret(): string {
  return (
    process.env.SESSION_JWT_SECRET ||
    (process.env.NODE_ENV !== 'production' ? 'dev-session-signing-key-jixiao2-local' : '')
  );
}

export function signSessionToken(
  sub: string,
  name: string,
  roles: string[],
  feishuOpenId?: string,
): string | null {
  const secret = getSecret();
  if (!secret) return null;
  const exp = Math.floor(Date.now() / 1000) + TTL_SEC;
  const payload: SessionPayload = {
    sub,
    name,
    roles,
    feishuOpenId,
    exp,
  };
  const json = Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
  const sig = createHmac('sha256', secret).update(json).digest('base64url');
  return `${json}.${sig}`;
}

export function verifySessionToken(token: string): SessionPayload | null {
  const secret = getSecret();
  if (!secret || !token) return null;
  const i = token.lastIndexOf('.');
  if (i <= 0) return null;
  const json = token.slice(0, i);
  const sig = token.slice(i + 1);
  const expected = createHmac('sha256', secret).update(json).digest('base64url');
  const a = Buffer.from(sig);
  const b = Buffer.from(expected);
  if (a.length !== b.length || !timingSafeEqual(a, b)) return null;
  let payload: SessionPayload;
  try {
    payload = JSON.parse(Buffer.from(json, 'base64url').toString('utf8')) as SessionPayload;
  } catch {
    return null;
  }
  if (!payload?.sub || typeof payload.exp !== 'number') return null;
  if (payload.exp < Math.floor(Date.now() / 1000)) return null;
  return payload;
}

export function readSessionFromRequest(cookies: Record<string, string | undefined> | undefined): SessionPayload | null {
  const raw = cookies?.jx_session;
  if (!raw) return null;
  return verifySessionToken(raw);
}

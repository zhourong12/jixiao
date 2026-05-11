import { Injectable, NestMiddleware } from '@nestjs/common';
import type { NextFunction, Request, Response } from 'express';
import { verifySessionToken } from '../utils/session-token';

@Injectable()
export class SessionUserContextMiddleware implements NestMiddleware {
  use(req: Request, _res: Response, next: NextFunction) {
    const raw = req.cookies?.jx_session as string | undefined;
    if (!raw) {
      next();
      return;
    }
    const payload = verifySessionToken(raw);
    if (!payload) {
      next();
      return;
    }
    const prev = (req.userContext ?? {}) as Record<string, unknown>;
    req.userContext = {
      ...prev,
      userId: payload.sub,
      userName: payload.name,
      roles: payload.roles?.length ? payload.roles : (prev.roles as string[] | undefined) ?? [],
    } as typeof req.userContext;
    next();
  }
}

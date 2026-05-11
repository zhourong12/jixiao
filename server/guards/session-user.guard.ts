import { CanActivate, ExecutionContext, Injectable, UnauthorizedException } from '@nestjs/common';
import type { Request } from 'express';

/**
 * 要求请求已通过会话中间件写入 userId（与飞书 OAuth / 账密登录签发的 Cookie 一致）。
 */
@Injectable()
export class SessionUserGuard implements CanActivate {
  canActivate(context: ExecutionContext): boolean {
    const req = context.switchToHttp().getRequest<Request>();
    const userId = req.userContext?.userId;
    if (!userId) {
      throw new UnauthorizedException('请先登录');
    }
    return true;
  }
}

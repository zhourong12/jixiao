import { Body, Controller, Post, Res } from '@nestjs/common';
import type { Response } from 'express';
import { AuthService } from './auth.service';

const SESSION_COOKIE = 'jx_session';
const SESSION_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;

@Controller('auth')
export class LocalAuthController {
  constructor(private readonly auth: AuthService) {}

  /** 账密登录（默认密码 123456），与飞书 OAuth 共用 jx_session Cookie */
  @Post('password/login')
  async passwordLogin(
    @Body() body: { username?: string; password?: string },
    @Res({ passthrough: true }) res: Response,
  ) {
    const sessionVal = await this.auth.loginWithPassword(body.username ?? '', body.password ?? '');
    if (!sessionVal) {
      return { success: false, message: '未配置 SESSION_JWT_SECRET，无法签发会话' };
    }
    res.cookie(SESSION_COOKIE, sessionVal, {
      httpOnly: true,
      sameSite: 'lax',
      path: '/',
      maxAge: SESSION_MAX_AGE_MS,
      secure: process.env.NODE_ENV === 'production',
    });
    return { success: true };
  }
}

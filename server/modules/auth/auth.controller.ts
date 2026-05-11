import { Controller, Get, Query, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { randomBytes } from 'node:crypto';
import { AuthService } from './auth.service';
import { FeishuCredentialsService } from '../../database/feishu-credentials.service';

const STATE_COOKIE = 'feishu_oauth_state';
const SESSION_COOKIE = 'jx_session';
const STATE_MAX_AGE_MS = 10 * 60 * 1000;
const SESSION_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000;

@Controller('auth/feishu')
export class AuthController {
  constructor(
    private readonly auth: AuthService,
    private readonly feishuCredentials: FeishuCredentialsService,
  ) {}

  @Get('login')
  async login(@Res() res: Response) {
    const appId = await this.feishuCredentials.resolveAppIdOnly();
    const redirectUri =
      process.env.FEISHU_REDIRECT_URI ||
      (process.env.NODE_ENV !== 'production'
        ? `http://127.0.0.1:${Number(process.env.SERVER_PORT || 3000)}/auth/feishu/callback`
        : '');
    if (!redirectUri) {
      res.status(500).send('生产环境请配置 FEISHU_REDIRECT_URI');
      return;
    }
    const state = randomBytes(16).toString('hex');
    res.cookie(STATE_COOKIE, state, {
      httpOnly: true,
      sameSite: 'lax',
      path: '/',
      maxAge: STATE_MAX_AGE_MS,
      secure: process.env.NODE_ENV === 'production',
    });
    const url = new URL('https://open.feishu.cn/open-apis/authen/v1/index');
    url.searchParams.set('app_id', appId);
    url.searchParams.set('redirect_uri', redirectUri);
    url.searchParams.set('state', state);
    res.redirect(302, url.toString());
  }

  @Get('callback')
  async callback(
    @Query('code') code: string | undefined,
    @Query('state') state: string | undefined,
    @Req() req: Request,
    @Res() res: Response,
  ) {
    const base = process.env.CLIENT_BASE_PATH || '/';
    const fail = (msg: string) => {
      const q = new URLSearchParams({ login_error: msg });
      res.redirect(302, `${base}?${q.toString()}`);
    };

    if (!code) {
      fail('缺少授权码');
      return;
    }
    const cookieState = req.cookies?.[STATE_COOKIE] as string | undefined;
    if (!state || !cookieState || state !== cookieState) {
      fail('state 校验失败');
      return;
    }
    res.clearCookie(STATE_COOKIE, { path: '/' });

    try {
      const token = await this.auth.exchangeCodeForUserAccessToken(code);
      const { name, openId } = await this.auth.fetchFeishuUserInfo(token);
      const emp = await this.auth.resolveEmployeeByFeishu(openId, name);
      const roles = await this.auth.loadRolesForUser(emp.employeeId);
      const sessionVal = this.auth.buildSessionCookieValue(emp.employeeId, emp.name, roles, openId);
      if (!sessionVal) {
        fail('未配置 SESSION_JWT_SECRET，无法签发会话');
        return;
      }
      res.cookie(SESSION_COOKIE, sessionVal, {
        httpOnly: true,
        sameSite: 'lax',
        path: '/',
        maxAge: SESSION_MAX_AGE_MS,
        secure: process.env.NODE_ENV === 'production',
      });
      res.redirect(302, base);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '登录失败';
      fail(msg);
    }
  }

  @Get('logout')
  logout(@Res() res: Response) {
    res.clearCookie(SESSION_COOKIE, { path: '/' });
    const base = process.env.CLIENT_BASE_PATH || '/';
    res.redirect(302, base);
  }
}

import { Controller, Get, Post, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { MenuPermissionService } from '../menu-permission/menu-permission.service';

const SESSION_COOKIE = 'jx_session';

@Controller('api/session')
export class SessionController {
  constructor(private readonly menuPermissionService: MenuPermissionService) {}

  /** 账密 / 飞书登录后：用户基础信息 + 角色组 + 可访问菜单（与 RBAC 表一致） */
  @Get('me')
  async me(@Req() req: Request) {
    const ctx = req.userContext;
    if (!ctx?.userId) {
      return { authenticated: false as const };
    }
    const perm = await this.menuPermissionService.getEffectiveMenusForUser(ctx.userId);
    return {
      authenticated: true as const,
      user_id: ctx.userId,
      name: ctx.userName || ctx.userId,
      roles: perm.roles,
      role: perm.role,
      menus: perm.menus,
    };
  }

  /** 清除本地会话 Cookie（与 /auth/feishu/logout 行为一致） */
  @Post('logout')
  logout(@Res({ passthrough: true }) res: Response) {
    res.clearCookie(SESSION_COOKIE, { path: '/' });
    return { success: true };
  }
}

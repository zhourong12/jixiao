import { BadRequestException, Inject, Injectable, Logger, UnauthorizedException } from '@nestjs/common';
import { eq } from 'drizzle-orm';
import { DRIZZLE_DATABASE } from '@lark-apaas/fullstack-nestjs-core';
import type { AppDatabase } from '@server/database/app-database';
import { employeeHierarchy } from '../../database/schema';
import { MenuPermissionService } from '../menu-permission/menu-permission.service';
import { FeishuCredentialsService } from '../../database/feishu-credentials.service';
import { signSessionToken } from '../../utils/session-token';

type FeishuTokenResponse = {
  code?: number;
  msg?: string;
  data?: { access_token?: string; expires_in?: number };
};

type FeishuUserInfoResponse = {
  code?: number;
  msg?: string;
  data?: {
    name?: string;
    open_id?: string;
    union_id?: string;
    user_id?: string;
    email?: string;
    avatar_url?: string;
  };
};

/** 本地账密登录默认口令（演示环境；生产请改为可配置策略） */
const DEFAULT_LOCAL_PASSWORD = '123456';

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    @Inject(DRIZZLE_DATABASE) private readonly db: AppDatabase,
    private readonly feishuCredentials: FeishuCredentialsService,
    private readonly menuPermissionService: MenuPermissionService,
  ) {}

  async exchangeCodeForUserAccessToken(code: string): Promise<string> {
    const { appId, appSecret } = await this.feishuCredentials.resolve();
    const res = await fetch('https://open.feishu.cn/open-apis/authen/v1/access_token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json; charset=utf-8' },
      body: JSON.stringify({
        grant_type: 'authorization_code',
        code,
        app_id: appId,
        app_secret: appSecret,
      }),
    });
    const json = (await res.json()) as FeishuTokenResponse;
    if (json.code !== 0 || !json.data?.access_token) {
      this.logger.warn(`飞书换票失败: ${json.code} ${json.msg}`);
      throw new BadRequestException(json.msg || '飞书授权换票失败');
    }
    return json.data.access_token;
  }

  async fetchFeishuUserInfo(accessToken: string): Promise<{ name: string; openId: string }> {
    const res = await fetch('https://open.feishu.cn/open-apis/authen/v1/user_info', {
      method: 'GET',
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    const json = (await res.json()) as FeishuUserInfoResponse;
    if (json.code !== 0 || !json.data?.open_id) {
      this.logger.warn(`飞书用户信息失败: ${json.code} ${json.msg}`);
      throw new BadRequestException(json.msg || '获取飞书用户信息失败');
    }
    const d = json.data;
    const name = typeof d.name === 'string' ? d.name.trim() : '';
    if (!name) {
      throw new BadRequestException('飞书账号缺少姓名，无法匹配员工');
    }
    return { name, openId: d.open_id };
  }

  async resolveEmployeeByFeishu(openId: string, feishuName: string): Promise<{ employeeId: string; name: string }> {
    const byOpen = await this.db
      .select({
        employeeId: employeeHierarchy.employeeId,
        name: employeeHierarchy.name,
      })
      .from(employeeHierarchy)
      .where(eq(employeeHierarchy.employeeId, openId))
      .limit(1);
    if (byOpen.length) {
      return {
        employeeId: byOpen[0].employeeId,
        name: (byOpen[0].name && byOpen[0].name.trim()) || feishuName,
      };
    }

    const byName = await this.db
      .select({
        employeeId: employeeHierarchy.employeeId,
        name: employeeHierarchy.name,
      })
      .from(employeeHierarchy)
      .where(eq(employeeHierarchy.name, feishuName));

    if (byName.length === 0) {
      throw new BadRequestException(`未找到与飞书姓名「${feishuName}」或 open_id 对应的员工，请在员工管理中维护`);
    }
    if (byName.length > 1) {
      throw new BadRequestException(`存在多名员工姓名为「${feishuName}」，请用飞书 open_id 作为员工编号同步`);
    }
    return {
      employeeId: byName[0].employeeId,
      name: (byName[0].name && byName[0].name.trim()) || feishuName,
    };
  }

  async loadRolesForUser(employeeId: string): Promise<string[]> {
    const keys = await this.menuPermissionService.getRoleKeysForUser(employeeId);
    return keys.length ? keys : ['employee'];
  }

  buildSessionCookieValue(employeeId: string, displayName: string, roles: string[], openId?: string): string | null {
    return signSessionToken(employeeId, displayName, roles, openId);
  }

  /**
   * 使用员工编号（employee_id）或姓名 + 默认密码登录，签发与飞书一致的会话 Cookie 值。
   */
  async loginWithPassword(username: string, password: string): Promise<string | null> {
    const u = typeof username === 'string' ? username.trim() : '';
    if (!u) {
      throw new UnauthorizedException('请输入用户名');
    }
    if (password !== DEFAULT_LOCAL_PASSWORD) {
      throw new UnauthorizedException('用户名或密码错误');
    }

    const byId = await this.db
      .select({
        employeeId: employeeHierarchy.employeeId,
        name: employeeHierarchy.name,
      })
      .from(employeeHierarchy)
      .where(eq(employeeHierarchy.employeeId, u))
      .limit(1);

    let row: { employeeId: string; name: string | null } | undefined = byId[0];
    if (!row) {
      const byName = await this.db
        .select({
          employeeId: employeeHierarchy.employeeId,
          name: employeeHierarchy.name,
        })
        .from(employeeHierarchy)
        .where(eq(employeeHierarchy.name, u));
      if (byName.length === 0) {
        throw new UnauthorizedException('用户不存在');
      }
      if (byName.length > 1) {
        throw new BadRequestException('存在重名员工，请使用员工编号（employee_id）登录');
      }
      row = byName[0];
    }

    const displayName = (row.name && row.name.trim()) || u;
    const roles = await this.loadRolesForUser(row.employeeId);
    return this.buildSessionCookieValue(row.employeeId, displayName, roles);
  }
}

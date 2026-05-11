import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  Inject,
  NotFoundException,
} from '@nestjs/common';
import { eq, inArray, asc, sql, count } from 'drizzle-orm';
import { DRIZZLE_DATABASE } from '@lark-apaas/fullstack-nestjs-core';
import type { AppDatabase } from '@server/database/app-database';
import { rbacMenu, rbacRole, rbacUserRole, rbacRoleMenu } from '@server/database/schema';
import type {
  MenuPermissionKey,
  MenuPermissionsMeResponse,
  MenuPermissionMatrixResponse,
  UpdateMenuPermissionsBody,
  CreateRbacRoleRequest,
  UpdateRbacRoleRequest,
  RbacRoleItem,
} from '@shared/api.interface';
import { MENU_PERMISSION_KEYS } from '@shared/api.interface';

function allMenusTrue(): Record<MenuPermissionKey, boolean> {
  return Object.fromEntries(MENU_PERMISSION_KEYS.map((k) => [k, true])) as Record<
    MenuPermissionKey,
    boolean
  >;
}

@Injectable()
export class MenuPermissionService {
  constructor(@Inject(DRIZZLE_DATABASE) private readonly db: AppDatabase) {}

  /** 用户绑定的所有角色 key；无记录时由调用方视为仅 employee */
  async getRoleKeysForUser(userId: string): Promise<string[]> {
    const rows = await this.db
      .select({ roleKey: rbacUserRole.roleKey })
      .from(rbacUserRole)
      .where(eq(rbacUserRole.userId, userId));
    return rows.map((r) => r.roleKey);
  }

  /**
   * 主角色（展示用）：super_admin > admin > 单角色原样返回 > 多角色时优先非 employee 的第一个 > employee
   */
  async getUserRole(userId: string): Promise<string> {
    const keys = await this.getRoleKeysForUser(userId);
    if (keys.length === 0) return 'employee';
    if (keys.includes('super_admin')) return 'super_admin';
    if (keys.includes('admin')) return 'admin';
    if (keys.length === 1) return keys[0]!;
    const nonEmp = keys.find((k) => k !== 'employee');
    return nonEmp ?? 'employee';
  }

  async getEffectiveMenusForUser(userId: string): Promise<MenuPermissionsMeResponse> {
    const roleKeys = await this.getRoleKeysForUser(userId);
    const effectiveRoleKeys = roleKeys.length ? roleKeys : ['employee'];
    const primaryRole = await this.getUserRole(userId);

    if (effectiveRoleKeys.includes('super_admin')) {
      return {
        role: primaryRole,
        roles: effectiveRoleKeys,
        menus: allMenusTrue(),
      };
    }

    const rows = await this.db
      .select({ menuKey: rbacRoleMenu.menuKey, allowed: rbacRoleMenu.allowed })
      .from(rbacRoleMenu)
      .where(inArray(rbacRoleMenu.roleKey, effectiveRoleKeys));

    const menus = Object.fromEntries(MENU_PERMISSION_KEYS.map((k) => [k, false])) as Record<
      MenuPermissionKey,
      boolean
    >;
    for (const r of rows) {
      const key = r.menuKey as MenuPermissionKey;
      if (MENU_PERMISSION_KEYS.includes(key) && r.allowed) {
        menus[key] = true;
      }
    }

    /** 导出绩效、批量创建、上级评分校准队列仅超管，忽略库中误配 */
    menus.performance_export = false;
    menus.performance_batch_create = false;
    menus.admin_performance_calibration = false;

    return { role: primaryRole, roles: effectiveRoleKeys, menus };
  }

  async assertMenuAllowed(userId: string, key: MenuPermissionKey): Promise<void> {
    const { menus } = await this.getEffectiveMenusForUser(userId);
    if (menus[key] === false) {
      throw new ForbiddenException('当前角色无此操作权限');
    }
  }

  assertSuperAdmin(role: string, message?: string): void {
    if (role !== 'super_admin') {
      throw new ForbiddenException(message ?? '仅超级管理员可配置菜单权限');
    }
  }

  /** 在员工管理中设置系统角色（写入 user_role） */
  async assertCanManageEmployeeRoles(requesterId: string): Promise<void> {
    const role = await this.getUserRole(requesterId);
    if (role !== 'admin' && role !== 'super_admin') {
      throw new ForbiddenException('仅管理员可设置员工系统角色');
    }
  }

  async getMatrix(requesterId: string): Promise<MenuPermissionMatrixResponse> {
    const requesterRole = await this.getUserRole(requesterId);
    this.assertSuperAdmin(requesterRole);

    const roleRows = await this.db
      .select({
        roleKey: rbacRole.roleKey,
        name: rbacRole.name,
        isSystem: rbacRole.isSystem,
      })
      .from(rbacRole)
      .orderBy(asc(rbacRole.sortOrder));
    const roles = roleRows.map((r) => r.roleKey);
    const roleInfos = roleRows.map((r) => ({
      roleKey: r.roleKey,
      name: r.name,
      isSystem: Boolean(r.isSystem),
    }));

    const menuRows = await this.db
      .select({ menuKey: rbacMenu.menuKey, sortOrder: rbacMenu.sortOrder })
      .from(rbacMenu);
    const sortByKey = new Map(menuRows.map((r) => [r.menuKey as MenuPermissionKey, r.sortOrder]));
    /** 以代码中的 MENU_PERMISSION_KEYS 为全集，避免 menu 表漏插某行时权限页不显示该项（如 admin_roles） */
    const menus = [...MENU_PERMISSION_KEYS].sort((a, b) => {
      const sa = sortByKey.get(a) ?? 10_000;
      const sb = sortByKey.get(b) ?? 10_000;
      if (sa !== sb) return sa - sb;
      return a.localeCompare(b);
    });

    const rmRows = await this.db.select().from(rbacRoleMenu);
    const matrix: MenuPermissionMatrixResponse['matrix'] = {};
    for (const rk of roles) {
      const row: Partial<Record<MenuPermissionKey, boolean>> = {};
      for (const mk of MENU_PERMISSION_KEYS) {
        row[mk] = false;
      }
      matrix[rk] = row;
    }
    for (const row of rmRows) {
      const mk = row.menuKey as MenuPermissionKey;
      if (!matrix[row.roleKey] || !MENU_PERMISSION_KEYS.includes(mk)) continue;
      matrix[row.roleKey]![mk] = Boolean(row.allowed);
    }

    return { roles, roleInfos, menus, matrix };
  }

  async updateRoleMenus(requesterId: string, body: UpdateMenuPermissionsBody): Promise<void> {
    const requesterRole = await this.getUserRole(requesterId);
    this.assertSuperAdmin(requesterRole);

    if (body.role === 'super_admin') {
      return;
    }

    const exists = await this.db
      .select({ roleKey: rbacRole.roleKey })
      .from(rbacRole)
      .where(eq(rbacRole.roleKey, body.role))
      .limit(1);
    if (!exists.length) {
      throw new ForbiddenException('未知角色');
    }

    const superAdminOnlyMenus: MenuPermissionKey[] = [
      'performance_export',
      'performance_batch_create',
      'admin_performance_calibration',
    ];
    const entries = Object.entries(body.menus ?? {}) as [MenuPermissionKey, boolean][];
    for (const [menuKey, allowed] of entries) {
      if (!MENU_PERMISSION_KEYS.includes(menuKey)) continue;
      if (superAdminOnlyMenus.includes(menuKey)) {
        if (allowed) {
          throw new BadRequestException(
            '导出绩效、批量创建与「绩效校准（上级评分）」仅超级管理员可用，不可授予其他角色',
          );
        }
        await this.db
          .insert(rbacRoleMenu)
          .values({ roleKey: body.role, menuKey, allowed: 0 })
          .onDuplicateKeyUpdate({ set: { allowed: 0 } });
        continue;
      }
      await this.db
        .insert(rbacRoleMenu)
        .values({ roleKey: body.role, menuKey, allowed: allowed ? 1 : 0 })
        .onDuplicateKeyUpdate({ set: { allowed: allowed ? 1 : 0 } });
    }
  }

  async listRbacRoles(requesterId: string): Promise<RbacRoleItem[]> {
    const requesterRole = await this.getUserRole(requesterId);
    this.assertSuperAdmin(requesterRole);
    const rows = await this.db
      .select({
        roleKey: rbacRole.roleKey,
        name: rbacRole.name,
        isSystem: rbacRole.isSystem,
        sortOrder: rbacRole.sortOrder,
      })
      .from(rbacRole)
      .orderBy(asc(rbacRole.sortOrder));
    return rows.map((r) => ({
      roleKey: r.roleKey,
      name: r.name,
      isSystem: Boolean(r.isSystem),
      sortOrder: r.sortOrder,
    }));
  }

  async createRbacRole(requesterId: string, body: CreateRbacRoleRequest): Promise<void> {
    const requesterRole = await this.getUserRole(requesterId);
    this.assertSuperAdmin(requesterRole);

    const roleKey = body.roleKey.trim();
    const name = body.name.trim();
    if (!/^[a-z][a-z0-9_]{1,47}$/.test(roleKey)) {
      throw new BadRequestException('角色标识须为小写字母开头，仅含小写字母、数字、下划线，长度 2～48');
    }
    if (!name) {
      throw new BadRequestException('请填写角色名称');
    }

    const dup = await this.db.select().from(rbacRole).where(eq(rbacRole.roleKey, roleKey)).limit(1);
    if (dup.length) {
      throw new BadRequestException('该角色标识已存在');
    }

    let sortOrder = body.sortOrder;
    if (sortOrder === undefined) {
      const maxRow = await this.db
        .select({ m: sql<number>`max(${rbacRole.sortOrder})`.mapWith(Number) })
        .from(rbacRole);
      sortOrder = (maxRow[0]?.m ?? 0) + 10;
    }

    await this.db.insert(rbacRole).values({
      roleKey,
      name,
      isSystem: 0,
      sortOrder,
    });

    for (const menuKey of MENU_PERMISSION_KEYS) {
      await this.db.insert(rbacRoleMenu).values({ roleKey, menuKey, allowed: 0 });
    }
  }

  async updateRbacRole(requesterId: string, roleKey: string, body: UpdateRbacRoleRequest): Promise<void> {
    const requesterRole = await this.getUserRole(requesterId);
    this.assertSuperAdmin(requesterRole);

    const existing = await this.db.select().from(rbacRole).where(eq(rbacRole.roleKey, roleKey)).limit(1);
    if (!existing.length) {
      throw new NotFoundException('角色不存在');
    }

    const setObj: Record<string, unknown> = {};
    if (body.name !== undefined) {
      const n = body.name.trim();
      if (!n) throw new BadRequestException('角色名称不能为空');
      setObj.name = n;
    }
    if (body.sortOrder !== undefined) {
      setObj.sortOrder = body.sortOrder;
    }
    if (Object.keys(setObj).length === 0) return;

    await this.db.update(rbacRole).set(setObj).where(eq(rbacRole.roleKey, roleKey));
  }

  async deleteRbacRole(requesterId: string, roleKey: string): Promise<void> {
    const requesterRole = await this.getUserRole(requesterId);
    this.assertSuperAdmin(requesterRole);

    const row = await this.db.select().from(rbacRole).where(eq(rbacRole.roleKey, roleKey)).limit(1);
    if (!row.length) {
      throw new NotFoundException('角色不存在');
    }
    if (row[0].isSystem) {
      throw new ForbiddenException('系统内置角色不可删除');
    }

    const usage = await this.db
      .select({ c: count() })
      .from(rbacUserRole)
      .where(eq(rbacUserRole.roleKey, roleKey));
    if (Number(usage[0]?.c ?? 0) > 0) {
      throw new ForbiddenException('仍有用户绑定该角色，无法删除');
    }

    await this.db.delete(rbacRoleMenu).where(eq(rbacRoleMenu.roleKey, roleKey));
    await this.db.delete(rbacRole).where(eq(rbacRole.roleKey, roleKey));
  }
}

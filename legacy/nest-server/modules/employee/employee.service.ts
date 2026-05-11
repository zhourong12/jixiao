import { BadRequestException, Injectable, Inject, Logger } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { eq, count, or, and, like, isNotNull, ne, inArray, asc } from 'drizzle-orm';
import { DRIZZLE_DATABASE } from '@lark-apaas/fullstack-nestjs-core';
import type { AppDatabase } from '@server/database/app-database';
import { employeeHierarchy, rbacRole, rbacUserRole } from '../../database/schema';
import type { CreateEmployeeRequest, UpdateEmployeeRequest, DepartmentOption } from '@shared/api.interface';
import * as lark from '@larksuiteoapi/node-sdk';
import { FeishuCredentialsService } from '../../database/feishu-credentials.service';

export interface EmployeeHierarchyData {
  employeeId: string;
  managerId?: string;
  dottedManagerId?: string;
  departmentId?: string;
  departmentName?: string;
  name?: string;
  phone?: string;
  employeeNo?: string;
  employeeType?: string;
  position?: string;
  workLocation?: string;
  joinDate?: string;
  roleKey?: string;
  roleName?: string;
  managerName?: string;
  dottedManagerName?: string;
}

export interface EmployeeListItem {
  userId: string;
  name: string;
  avatar?: string;
  departmentName?: string;
  managerId?: string;
  managerName?: string;
  dottedManagerId?: string;
  dottedManagerName?: string;
}

@Injectable()
export class EmployeeService {
  private readonly logger = new Logger(EmployeeService.name);

  constructor(
    @Inject(DRIZZLE_DATABASE) private readonly db: AppDatabase,
    private readonly feishuCredentials: FeishuCredentialsService,
  ) {}

  private static pickPrimaryRoleKey(keys: string[]): string {
    if (keys.length === 0) return 'employee';
    if (keys.includes('super_admin')) return 'super_admin';
    if (keys.includes('admin')) return 'admin';
    if (keys.length === 1) return keys[0]!;
    const nonEmp = keys.find((k) => k !== 'employee');
    return nonEmp ?? 'employee';
  }

  async listAssignableRoles(): Promise<{ roleKey: string; name: string }[]> {
    const rows = await this.db
      .select({ roleKey: rbacRole.roleKey, name: rbacRole.name })
      .from(rbacRole)
      .orderBy(asc(rbacRole.sortOrder));
    return rows;
  }

  /** 替换为单一主角色；空字符串则删除 user_role（与未配置一致，权限上视为员工） */
  async setUserPrimaryRole(userId: string, roleKey: string): Promise<void> {
    const trimmed = roleKey.trim();
    if (!trimmed) {
      await this.db.delete(rbacUserRole).where(eq(rbacUserRole.userId, userId));
      return;
    }
    const exists = await this.db
      .select({ roleKey: rbacRole.roleKey })
      .from(rbacRole)
      .where(eq(rbacRole.roleKey, trimmed))
      .limit(1);
    if (exists.length === 0) {
      throw new BadRequestException('角色不存在');
    }
    await this.db.delete(rbacUserRole).where(eq(rbacUserRole.userId, userId));
    await this.db.insert(rbacUserRole).values({ userId, roleKey: trimmed });
  }

  private async enrichItemsWithRoles(items: EmployeeHierarchyData[]): Promise<void> {
    if (items.length === 0) return;
    const ids = items.map((i) => i.employeeId);
    const urRows = await this.db
      .select({ userId: rbacUserRole.userId, roleKey: rbacUserRole.roleKey })
      .from(rbacUserRole)
      .where(inArray(rbacUserRole.userId, ids));

    const keysByUser = new Map<string, string[]>();
    for (const row of urRows) {
      const list = keysByUser.get(row.userId) ?? [];
      list.push(row.roleKey);
      keysByUser.set(row.userId, list);
    }

    const roleRows = await this.db
      .select({ roleKey: rbacRole.roleKey, name: rbacRole.name })
      .from(rbacRole);
    const nameByKey = new Map(roleRows.map((r) => [r.roleKey, r.name]));

    for (const item of items) {
      const keys = keysByUser.get(item.employeeId) ?? [];
      const primary = EmployeeService.pickPrimaryRoleKey(keys);
      item.roleKey = primary;
      item.roleName = nameByKey.get(primary) ?? primary;
    }
  }

  async getEmployeeHierarchy(employeeId: string): Promise<EmployeeHierarchyData | null> {
    const result = await this.db
      .select({
        employeeId: employeeHierarchy.employeeId,
        managerId: employeeHierarchy.managerId,
        dottedManagerId: employeeHierarchy.dottedManagerId,
        departmentId: employeeHierarchy.departmentId,
        departmentName: employeeHierarchy.departmentName,
      })
      .from(employeeHierarchy)
      .where(eq(employeeHierarchy.employeeId, employeeId))
      .limit(1);

    return result.length > 0 ? result[0] : null;
  }

  async upsertEmployeeHierarchy(data: EmployeeHierarchyData): Promise<void> {
    const existing = await this.getEmployeeHierarchy(data.employeeId);

    if (existing) {
      await this.db
        .update(employeeHierarchy)
        .set({
          managerId: data.managerId ?? null,
          dottedManagerId: data.dottedManagerId ?? null,
          departmentId: data.departmentId,
          departmentName: data.departmentName,
        })
        .where(eq(employeeHierarchy.employeeId, data.employeeId));
    } else {
      await this.db.insert(employeeHierarchy).values({
        employeeId: data.employeeId,
        managerId: data.managerId ?? null,
        dottedManagerId: data.dottedManagerId ?? null,
        departmentId: data.departmentId,
        departmentName: data.departmentName,
      });
    }
  }

  async getAllHierarchies(params: {
    page: number;
    pageSize: number;
    keyword?: string;
  }): Promise<{ items: EmployeeHierarchyData[]; total: number }> {
    const { page, pageSize, keyword } = params;

    const conditions = [];
    if (keyword) {
      conditions.push(
        or(
          like(employeeHierarchy.name, `%${keyword}%`),
          like(employeeHierarchy.phone, `%${keyword}%`),
          like(employeeHierarchy.employeeNo, `%${keyword}%`),
          like(employeeHierarchy.position, `%${keyword}%`),
          like(employeeHierarchy.departmentName, `%${keyword}%`),
        ),
      );
    }

    const whereClause = conditions.length > 0 ? and(...conditions) : undefined;

    const totalResult = await this.db
      .select({ count: count() })
      .from(employeeHierarchy)
      .where(whereClause);
    const total = Number(totalResult[0].count);

    // 查询员工列表，同时关联查询上级的姓名
    const result = await this.db
      .select({
        employeeId: employeeHierarchy.employeeId,
        managerId: employeeHierarchy.managerId,
        dottedManagerId: employeeHierarchy.dottedManagerId,
        departmentId: employeeHierarchy.departmentId,
        departmentName: employeeHierarchy.departmentName,
        name: employeeHierarchy.name,
        phone: employeeHierarchy.phone,
        employeeNo: employeeHierarchy.employeeNo,
        employeeType: employeeHierarchy.employeeType,
        position: employeeHierarchy.position,
        workLocation: employeeHierarchy.workLocation,
        joinDate: employeeHierarchy.joinDate,
      })
      .from(employeeHierarchy)
      .where(whereClause)
      .limit(pageSize)
      .offset((page - 1) * pageSize);

    // 获取所有上级ID，构建姓名映射
    const managerIds = result.map((r) => r.managerId).filter((id): id is string => !!id);
    const dottedManagerIds = result.map((r) => r.dottedManagerId).filter((id): id is string => !!id);
    const allManagerIds = [...new Set([...managerIds, ...dottedManagerIds])];

    let managerNameMap = new Map<string, string>();
    if (allManagerIds.length > 0) {
      // 使用 IN 查询代替 ANY，避免数组格式问题
      const managers = await this.db
        .select({
          userId: employeeHierarchy.employeeId,
          name: employeeHierarchy.name,
        })
        .from(employeeHierarchy)
        .where(inArray(employeeHierarchy.employeeId, allManagerIds));

      managerNameMap = new Map(managers.map((m) => [m.userId, m.name || '']));
    }

    // 组装返回结果，添加上级姓名
    const itemsWithManagerName = result.map((item) => ({
      ...item,
      managerName: item.managerId ? managerNameMap.get(item.managerId) || item.managerId : undefined,
      dottedManagerName: item.dottedManagerId ? managerNameMap.get(item.dottedManagerId) || item.dottedManagerId : undefined,
    }));

    await this.enrichItemsWithRoles(itemsWithManagerName);

    return { items: itemsWithManagerName, total };
  }

  async getAllDepartments(): Promise<string[]> {
    const result = await this.db
      .select({ departmentName: employeeHierarchy.departmentName })
      .from(employeeHierarchy)
      .where(and(isNotNull(employeeHierarchy.departmentName), ne(employeeHierarchy.departmentName, '')));

    const departments = [...new Set(result.map((r) => r.departmentName).filter(Boolean))];
    return departments.sort();
  }

  /** 去重后的部门 id + 名称（用于多选筛选，按 department_id 关联） */
  async listDepartmentOptions(): Promise<DepartmentOption[]> {
    const rows = await this.db
      .select({
        id: employeeHierarchy.departmentId,
        name: employeeHierarchy.departmentName,
      })
      .from(employeeHierarchy)
      .where(and(isNotNull(employeeHierarchy.departmentId), ne(employeeHierarchy.departmentId, '')))
      .groupBy(employeeHierarchy.departmentId, employeeHierarchy.departmentName);

    const list: DepartmentOption[] = rows
      .filter((r): r is { id: string; name: string | null } => !!r.id)
      .map((r) => ({ id: r.id, name: r.name ?? null }));
    list.sort((a, b) => (a.name || a.id).localeCompare(b.name || b.id, 'zh'));
    return list;
  }

  async createEmployee(data: CreateEmployeeRequest): Promise<{ id: string }> {
    const id = randomUUID();
    await this.db.insert(employeeHierarchy).values({
      id,
      employeeId: data.userId,
      name: data.name,
      phone: data.phone,
      employeeNo: data.employeeNo,
      employeeType: data.employeeType,
      position: data.position,
      workLocation: data.workLocation,
      joinDate: data.joinDate,
      departmentName: data.departmentName,
      managerId: data.managerId ?? null,
      dottedManagerId: data.dottedManagerId ?? null,
    });

    if (data.roleKey != null && data.roleKey.trim() !== '') {
      await this.setUserPrimaryRole(data.userId, data.roleKey);
    }

    return { id };
  }

  async updateEmployee(
    employeeId: string,
    data: UpdateEmployeeRequest,
  ): Promise<void> {
    const setObj: Record<string, unknown> = {};

    if (data.name !== undefined) setObj.name = data.name;
    if (data.phone !== undefined) setObj.phone = data.phone;
    if (data.employeeNo !== undefined) setObj.employeeNo = data.employeeNo;
    if (data.employeeType !== undefined) setObj.employeeType = data.employeeType;
    if (data.position !== undefined) setObj.position = data.position;
    if (data.workLocation !== undefined) setObj.workLocation = data.workLocation;
    if (data.joinDate !== undefined) setObj.joinDate = data.joinDate;
    if (data.departmentName !== undefined) setObj.departmentName = data.departmentName;
    if (data.managerId !== undefined) {
      setObj.managerId = data.managerId ?? null;
    }
    if (data.dottedManagerId !== undefined) {
      setObj.dottedManagerId = data.dottedManagerId ?? null;
    }

    if (Object.keys(setObj).length > 0) {
      await this.db
        .update(employeeHierarchy)
        .set(setObj)
        .where(eq(employeeHierarchy.employeeId, employeeId));
    }

    if (data.roleKey !== undefined) {
      await this.setUserPrimaryRole(employeeId, data.roleKey);
    }
  }

  async deleteEmployee(employeeId: string): Promise<void> {
    await this.db.delete(rbacUserRole).where(eq(rbacUserRole.userId, employeeId));
    await this.db
      .delete(employeeHierarchy)
      .where(eq(employeeHierarchy.employeeId, employeeId));
  }

  async clearAllEmployees(): Promise<void> {
    await this.db.delete(employeeHierarchy);
    this.logger.log('已清空所有员工数据');
  }

  async syncFromLark(clearExisting: boolean): Promise<{ count: number; totalCount: number; validCount: number; skippedCount: number }> {
    this.logger.log('开始同步飞书员工数据...');

    try {
      // 如果要求清空，先清空现有数据
      if (clearExisting) {
        await this.clearAllEmployees();
      }
      const { appId, appSecret } = await this.feishuCredentials.resolve();
      this.logger.log(`使用飞书应用: ${appId}`);
      const client = new lark.Client({
        appId,
        appSecret,
        appType: lark.AppType.SelfBuild,
        domain: lark.Domain.Feishu,
      });

      // 首先获取部门列表
      this.logger.log('正在获取部门列表...');
      const deptListRes = await client.contact.department.list({
        params: {
          page_size: 50,
          fetch_child: true,
          department_id_type: 'open_department_id',
        },
      });

      if (deptListRes.code !== 0) {
        this.logger.error(`飞书 API 错误 - 获取部门列表失败:`, JSON.stringify({
          code: deptListRes.code,
          msg: deptListRes.msg,
          data: deptListRes.data,
        }));
        throw new Error(`飞书API错误 [${deptListRes.code}]: ${deptListRes.msg}`);
      }

      const departments = deptListRes.data?.items || [];
      this.logger.log(`获取到 ${departments.length} 个部门`, JSON.stringify(departments.slice(0, 3).map((d: Record<string, unknown>) => ({ id: d.department_id, name: d.name, parent_id: d.parent_department_id }))));

      // 调用飞书通讯录API获取用户列表
      const allUsers: Array<{
        user_id?: string;
        name: string;
        avatar?: { avatar_72?: string };
        department_ids?: string[];
        leader_user_id?: string; // 直属上级
      }> = [];
      const userIdSet = new Set<string>(); // 用于去重

      // 遍历每个部门获取用户
      for (const dept of departments.slice(0, 20)) { // 限制最多查询20个部门
        const deptId = (dept as Record<string, unknown>).department_id as string;
        const deptName = (dept as Record<string, unknown>).name as string || '未命名部门';

        if (!deptId) {
          this.logger.warn('部门缺少 department_id，跳过');
          continue;
        }

        let pageToken: string | undefined;
        const pageSize = 50;
        let pageCount = 0;

        do {
          pageCount++;
          this.logger.log(`正在获取部门 ${deptName}(${deptId}) 第 ${pageCount} 页用户数据...`);

          const res = await client.contact.user.list({
            params: {
              department_id: deptId,
              page_size: pageSize,
              page_token: pageToken,
              user_id_type: 'open_id',
              department_id_type: 'open_department_id',
            },
          });

          if (res.code !== 0) {
            this.logger.error(`飞书 API 错误 - 获取部门 ${deptName} 用户失败:`, JSON.stringify({
              code: res.code,
              msg: res.msg,
              data: res.data,
              departmentId: deptId,
            }));
            throw new Error(`飞书API错误 [${res.code}]: ${res.msg}`);
          }

          this.logger.log(`部门 ${deptName} API 返回 ${res.data?.items?.length || 0} 个用户`);

          // 飞书 SDK 返回的数据类型与我们需要的不完全匹配，需要转换
          // 使用 open_id 作为用户唯一标识
          const items = (res.data?.items || [])
            .map((item: Record<string, unknown>) => ({
              user_id: (item.open_id || item.user_id) as string | undefined,
              name: item.name as string,
              avatar: item.avatar as { avatar_72?: string } | undefined,
              department_ids: item.department_ids as string[] | undefined,
              leader_user_id: (item.leader_user_id || item.leader_open_id) as string | undefined,
            }))
            .filter((item) => item.user_id && !userIdSet.has(item.user_id)); // 去重

          items.forEach((item) => {
            if (item.user_id) {
              userIdSet.add(item.user_id);
            }
          });

          this.logger.log(`部门 ${dept.name} 第 ${pageCount} 页获取到 ${items.length} 个用户`);
          allUsers.push(...items);
          pageToken = res.data?.page_token;
        } while (pageToken);
      }

      this.logger.log(`从飞书共获取到 ${allUsers.length} 个用户（去重后）`);

      if (allUsers.length === 0) {
        this.logger.warn('未能从飞书获取到任何用户数据，可能的原因：\n' +
          '1. 飞书应用没有开通通讯录权限\n' +
          '2. 应用的通讯录权限范围没有包含任何用户\n' +
          '3. 需要申请的权限：contact:user.base:readonly, contact:contact.base:readonly\n' +
          '4. 需要在飞书开放平台后台设置应用的通讯录访问范围');
        return {
          count: 0,
          totalCount: 0,
          validCount: 0,
          skippedCount: 0,
        };
      }

      // 获取部门名称映射
      const deptMap = new Map<string, string>();
      const deptIds = [...new Set(allUsers.flatMap((u) => u.department_ids || []))];

      for (const deptId of deptIds.slice(0, 50)) {
        try {
          const deptRes = await client.contact.department.get({
            path: { department_id: deptId },
            params: { department_id_type: 'open_department_id' },
          });
          if (deptRes.code === 0 && deptRes.data?.department) {
            deptMap.set(deptId, deptRes.data.department.name);
          }
        } catch (error: unknown) {
          this.logger.warn(`查询部门 ${deptId} 失败`, error instanceof Error ? error.message : String(error));
          // 忽略部门查询失败
        }
      }

      // 构建 user_id -> leader_user_id 映射
      const leaderMap = new Map<string, string>();
      allUsers.forEach((u) => {
        if (u.user_id && u.leader_user_id) {
          leaderMap.set(u.user_id, u.leader_user_id);
        }
      });

      // 转换为内部格式
      const users = allUsers
        .filter((u) => u.user_id)
        .map((u) => ({
          userId: u.user_id!,
          name: u.name,
          avatar: u.avatar?.avatar_72,
          departmentName: u.department_ids?.[0] ? deptMap.get(u.department_ids[0]) || '' : '',
          leaderUserId: u.leader_user_id,
        }));

      this.logger.log(`过滤后有效用户: ${users.length} 个`);

      // 过滤已存在的用户（如果不清空）
      const validCount = users.length;
      let usersToSync = users;
      if (!clearExisting) {
        const existingEmployees = await this.db
          .select({ employeeId: employeeHierarchy.employeeId })
          .from(employeeHierarchy);

        const existingIds = new Set(existingEmployees.map((e) => e.employeeId));
        usersToSync = users.filter((u) => !existingIds.has(u.userId));
        this.logger.log(`其中 ${usersToSync.length} 个为新用户需要同步`);
      }

      // 批量插入员工数据
      let syncedCount = 0;
      for (const user of usersToSync) {
        try {
          // 获取直属上级ID（从 leaderMap 查找，因为 leader_user_id 也是飞书 user_id）
          const managerId = user.leaderUserId || null;

          await this.db.insert(employeeHierarchy).values({
            employeeId: user.userId,
            managerId: managerId ?? null,
            name: user.name,
            departmentName: user.departmentName,
          });
          syncedCount++;
        } catch (error: unknown) {
          const errorMessage = error instanceof Error ? error.message : String(error);
          this.logger.error(`同步用户 ${user.userId} 失败: ${errorMessage}`);
        }
      }

      this.logger.log(`成功同步 ${syncedCount} 个员工`);
      return {
        count: syncedCount,
        totalCount: allUsers.length,
        validCount,
        skippedCount: validCount - usersToSync.length,
      };
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      this.logger.error('同步飞书员工失败', errorMessage);
      throw error;
    }
  }
}

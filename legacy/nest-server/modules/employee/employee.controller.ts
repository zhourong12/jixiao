import { Controller, Get, Post, Put, Delete, Body, Param, Query, Req, Logger } from '@nestjs/common';
import { NeedLogin } from '@lark-apaas/fullstack-nestjs-core';
import { EmployeeService } from './employee.service';
import { MenuPermissionService } from '../menu-permission/menu-permission.service';
import type { Request } from 'express';
import type {
  CreateEmployeeRequest,
  UpdateEmployeeRequest,
  EmployeeListResponse,
  EmployeeListItem,
  DepartmentOption,
  EmployeeRoleOption,
} from '@shared/api.interface';

interface UpdateHierarchyRequest {
  managerId?: string;
  dottedManagerId?: string;
  departmentId?: string;
  departmentName?: string;
}

@Controller('api/employees')
export class EmployeeController {
  private readonly logger = new Logger(EmployeeController.name);

  constructor(
    private readonly employeeService: EmployeeService,
    private readonly menuPermissionService: MenuPermissionService,
  ) {}

  @NeedLogin()
  @Get()
  async getEmployees(
    @Req() req: Request,
    @Query('page') page: string = '1',
    @Query('pageSize') pageSize: string = '20',
    @Query('keyword') keyword?: string,
  ): Promise<EmployeeListResponse> {
    const pageNum = parseInt(page, 10) || 1;
    const size = parseInt(pageSize, 10) || 20;

    const { items: hierarchies, total } = await this.employeeService.getAllHierarchies({
      page: pageNum,
      pageSize: size,
      keyword,
    });

    const employeeItems: EmployeeListItem[] = hierarchies.map((h) => ({
      id: h.employeeId,
      userId: h.employeeId,
      name: h.name || '',
      phone: h.phone,
      employeeNo: h.employeeNo,
      employeeType: h.employeeType,
      position: h.position,
      workLocation: h.workLocation,
      joinDate: h.joinDate,
      departmentName: h.departmentName,
      managerId: h.managerId,
      managerName: h.managerName,
      dottedManagerId: h.dottedManagerId,
      dottedManagerName: h.dottedManagerName,
      roleKey: h.roleKey,
      roleName: h.roleName,
    }));

    return {
      items: employeeItems,
      total,
      page: pageNum,
      pageSize: size,
    };
  }

  @NeedLogin()
  @Post()
  async createEmployee(@Req() req: Request, @Body() body: CreateEmployeeRequest) {
    if (body.roleKey != null && body.roleKey.trim() !== '') {
      await this.menuPermissionService.assertCanManageEmployeeRoles(req.userContext!.userId!);
    }
    return this.employeeService.createEmployee(body);
  }

  @NeedLogin()
  @Put(':id')
  async updateEmployee(
    @Req() req: Request,
    @Param('id') employeeId: string,
    @Body() body: UpdateEmployeeRequest,
  ) {
    if (body.roleKey !== undefined) {
      await this.menuPermissionService.assertCanManageEmployeeRoles(req.userContext!.userId!);
    }
    await this.employeeService.updateEmployee(employeeId, body);
    return { success: true };
  }

  @NeedLogin()
  @Delete(':id')
  async deleteEmployee(@Param('id') employeeId: string) {
    await this.employeeService.deleteEmployee(employeeId);
    return { success: true };
  }

  @NeedLogin()
  @Put(':id/hierarchy')
  async updateHierarchy(
    @Req() req: Request,
    @Param('id') employeeId: string,
    @Body() body: UpdateHierarchyRequest,
  ): Promise<{ success: boolean }> {
    await this.employeeService.upsertEmployeeHierarchy({
      employeeId,
      managerId: body.managerId,
      dottedManagerId: body.dottedManagerId,
      departmentId: body.departmentId,
      departmentName: body.departmentName,
    });

    return { success: true };
  }

  @NeedLogin()
  @Get('departments')
  async getDepartments(): Promise<{ items: string[] }> {
    const departments = await this.employeeService.getAllDepartments();
    return { items: departments };
  }

  @NeedLogin()
  @Get('department-options')
  async getDepartmentOptions(): Promise<{ items: DepartmentOption[] }> {
    const items = await this.employeeService.listDepartmentOptions();
    return { items };
  }

  @NeedLogin()
  @Get('role-options')
  async getRoleOptions(): Promise<{ items: EmployeeRoleOption[] }> {
    const items = await this.employeeService.listAssignableRoles();
    return { items };
  }

  @NeedLogin()
  @Get('all')
  async getAllEmployees(): Promise<{ items: EmployeeListItem[] }> {
    const { items: hierarchies } = await this.employeeService.getAllHierarchies({
      page: 1,
      pageSize: 10000,
    });

    const employeeItems: EmployeeListItem[] = hierarchies.map((h) => ({
      id: h.employeeId,
      userId: h.employeeId,
      name: h.name || '',
      phone: h.phone,
      employeeNo: h.employeeNo,
      employeeType: h.employeeType,
      position: h.position,
      workLocation: h.workLocation,
      joinDate: h.joinDate,
      departmentName: h.departmentName,
      managerId: h.managerId,
      managerName: h.managerName,
      dottedManagerId: h.dottedManagerId,
      dottedManagerName: h.dottedManagerName,
      roleKey: h.roleKey,
      roleName: h.roleName,
    }));

    return { items: employeeItems };
  }

  @NeedLogin()
  @Post('sync-from-lark')
  async syncFromLark(
    @Body() body: { clearExisting: boolean },
  ): Promise<{ success: boolean; syncedCount: number; totalCount?: number; validCount?: number; skippedCount?: number; message?: string }> {
    try {
      const result = await this.employeeService.syncFromLark(body.clearExisting);
      return {
        success: true,
        syncedCount: result.count,
        totalCount: result.totalCount,
        validCount: result.validCount,
        skippedCount: result.skippedCount,
      };
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      this.logger.error(`同步飞书失败: ${errorMessage}`);

      // 返回具体错误信息给前端
      return {
        success: false,
        syncedCount: 0,
        message: errorMessage,
      };
    }
  }
}

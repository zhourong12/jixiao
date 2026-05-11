import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
  Post,
  Put,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Request } from 'express';
import { SessionUserGuard } from '../../guards/session-user.guard';
import { MenuPermissionService } from './menu-permission.service';
import type {
  CreateRbacRoleRequest,
  UpdateMenuPermissionsBody,
  UpdateRbacRoleRequest,
} from '@shared/api.interface';

@Controller('api/menu-permissions')
@UseGuards(SessionUserGuard)
export class MenuPermissionController {
  constructor(private readonly menuPermissionService: MenuPermissionService) {}

  @Get('me')
  async me(@Req() req: Request) {
    return this.menuPermissionService.getEffectiveMenusForUser(req.userContext!.userId!);
  }
}

@Controller('api/admin/menu-permissions')
@UseGuards(SessionUserGuard)
export class MenuPermissionAdminController {
  constructor(private readonly menuPermissionService: MenuPermissionService) {}

  @Get('matrix')
  async matrix(@Req() req: Request) {
    return this.menuPermissionService.getMatrix(req.userContext!.userId!);
  }

  @Put()
  async update(@Req() req: Request, @Body() body: UpdateMenuPermissionsBody) {
    await this.menuPermissionService.updateRoleMenus(req.userContext!.userId!, body);
    return { success: true };
  }

  @Get('roles')
  async listRoles(@Req() req: Request) {
    const items = await this.menuPermissionService.listRbacRoles(req.userContext!.userId!);
    return { items };
  }

  @Post('roles')
  async createRole(@Req() req: Request, @Body() body: CreateRbacRoleRequest) {
    await this.menuPermissionService.createRbacRole(req.userContext!.userId!, body);
    return { success: true };
  }

  @Patch('roles/:roleKey')
  async patchRole(
    @Req() req: Request,
    @Param('roleKey') roleKey: string,
    @Body() body: UpdateRbacRoleRequest,
  ) {
    await this.menuPermissionService.updateRbacRole(req.userContext!.userId!, roleKey, body);
    return { success: true };
  }

  @Delete('roles/:roleKey')
  async removeRole(@Req() req: Request, @Param('roleKey') roleKey: string) {
    await this.menuPermissionService.deleteRbacRole(req.userContext!.userId!, roleKey);
    return { success: true };
  }
}

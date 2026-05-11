import { Module } from '@nestjs/common';
import { MenuPermissionController, MenuPermissionAdminController } from './menu-permission.controller';
import { MenuPermissionService } from './menu-permission.service';

@Module({
  controllers: [MenuPermissionController, MenuPermissionAdminController],
  providers: [MenuPermissionService],
  exports: [MenuPermissionService],
})
export class MenuPermissionModule {}

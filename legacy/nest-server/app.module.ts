import { APP_FILTER } from '@nestjs/core';
import { Module } from '@nestjs/common';
import { ScheduleModule } from '@nestjs/schedule';
import { PlatformModule } from '@lark-apaas/fullstack-nestjs-core';

import { DatabaseModule } from './database/database.module';
import { GlobalExceptionFilter } from './common/filters/exception.filter';
import { ViewModule } from './modules/view/view.module';
import { HomeModule } from './modules/home/home.module';
import { PerformanceModule } from './modules/performance/performance.module';
import { TemplateModule } from './modules/template/template.module';
import { NotificationModule } from './modules/notification/notification.module';
import { EmployeeModule } from './modules/employee/employee.module';
import { AuthModule } from './modules/auth/auth.module';
import { MenuPermissionModule } from './modules/menu-permission/menu-permission.module';
import { SchedulerModule } from './modules/scheduler/scheduler.module';
import { SystemConfigModule } from './modules/system-config/system-config.module';
import { EvaluationModule } from './modules/evaluation/evaluation.module';

@Module({
  imports: [
    DatabaseModule,
    ScheduleModule.forRoot(),
    // 平台 Module，提供平台能力
    PlatformModule.forRoot(),
    // ====== @route-section: business-modules START ======
    // Place all business modules here.Do NOT add fallback modules here.
    HomeModule,
    PerformanceModule,
    TemplateModule,
    NotificationModule,
    EmployeeModule,
    AuthModule,
    MenuPermissionModule,
    SystemConfigModule,
    EvaluationModule,
    SchedulerModule,
    // ====== @route-section: business-modules END ======

    // ⚠️ @route-order: last
    // ViewModule is the fallback route module, must be registered last.
    ViewModule,
  ],
  providers: [
    {
      provide: APP_FILTER,
      useClass: GlobalExceptionFilter,
    },
  ],
})
export class AppModule {}

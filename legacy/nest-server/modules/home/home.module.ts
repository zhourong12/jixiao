import { Module } from '@nestjs/common';
import { HomeController } from './home.controller';
import { HomeService } from './home.service';
import { MenuPermissionModule } from '../menu-permission/menu-permission.module';

@Module({
  imports: [MenuPermissionModule],
  controllers: [HomeController],
  providers: [HomeService],
  exports: [HomeService],
})
export class HomeModule {}

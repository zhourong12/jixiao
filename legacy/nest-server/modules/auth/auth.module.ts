import { MiddlewareConsumer, Module, NestModule } from '@nestjs/common';
import { AuthController } from './auth.controller';
import { LocalAuthController } from './local-auth.controller';
import { SessionController } from './session.controller';
import { AuthService } from './auth.service';
import { SessionUserContextMiddleware } from '../../middleware/session-user-context.middleware';
import { MenuPermissionModule } from '../menu-permission/menu-permission.module';

@Module({
  imports: [MenuPermissionModule],
  controllers: [AuthController, LocalAuthController, SessionController],
  providers: [AuthService],
})
export class AuthModule implements NestModule {
  configure(consumer: MiddlewareConsumer) {
    consumer.apply(SessionUserContextMiddleware).forRoutes('*');
  }
}

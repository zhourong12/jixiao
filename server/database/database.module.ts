import { Global, Module } from '@nestjs/common';
import { DRIZZLE_DATABASE } from '@lark-apaas/fullstack-nestjs-core';
import { DrizzleProvider } from './drizzle.provider';
import { FeishuCredentialsService } from './feishu-credentials.service';

@Global()
@Module({
  providers: [
    DrizzleProvider,
    {
      provide: DRIZZLE_DATABASE,
      useFactory: (p: DrizzleProvider) => p.db,
      inject: [DrizzleProvider],
    },
    FeishuCredentialsService,
  ],
  exports: [DRIZZLE_DATABASE, FeishuCredentialsService],
})
export class DatabaseModule {}

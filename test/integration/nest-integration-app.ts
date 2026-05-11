import { NestFactory } from '@nestjs/core';
import type { NestExpressApplication } from '@nestjs/platform-express';
import { configureApp } from '@lark-apaas/fullstack-nestjs-core';

/**
 * 启动与 main.ts 一致配置的 Nest 应用（供集成测试）。
 * 需配置 MYSQL_* 环境变量；未配置时调用方应跳过测试。
 * AppModule 使用动态 import，避免 Jest 加载本文件时解析 @Render 等装饰器失败。
 */
export async function createIntegrationApp(): Promise<NestExpressApplication> {
  if (!process.env.SESSION_JWT_SECRET) {
    process.env.SESSION_JWT_SECRET = 'dev-session-signing-key-jixiao2-local';
  }
  const { AppModule } = await import('../../server/app.module');
  const app = await NestFactory.create<NestExpressApplication>(AppModule, {
    logger: false,
    abortOnError: false,
  });
  await configureApp(app, { disableSwagger: true });
  await app.init();
  return app;
}
